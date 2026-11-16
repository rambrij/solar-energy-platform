package com.saumya.pos.platform;

import com.saumya.pos.platform.ConfigurationPolicy.Key;
import com.saumya.pos.platform.InvoiceMath.Interval;
import com.saumya.pos.platform.SubscriptionLifecycle.State;
import com.saumya.pos.platform.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.tenant.TenantStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant subscription, invoice and payment administration. Uses only platform-owned tables and the platform merchant
 * account configuration; it never touches vendor orders, invoices or vendor payment records (SUB-010).
 */
@Service @Transactional
public class SubscriptionAdminService {
    public record SubscriptionView(String id,long tenantId,String planCode,int planVersion,Interval interval,BigDecimal price,String currency,BigDecimal taxRate,boolean taxInclusive,int graceDays,Integer maxUsers,Integer maxBranches,Integer maxProducts,List<String> features,State status,String statusReason,Instant periodStart,Instant periodEnd,boolean effective,Instant createdAt,String createdBy) {}
    public record PaymentView(String id,BigDecimal amount,String currency,String method,String status,String merchantId,String reference,String recordedBy,Instant recordedAt) {}
    public record InvoiceView(String id,long tenantId,String subscriptionId,String number,Instant periodStart,Instant periodEnd,BigDecimal amount,BigDecimal tax,BigDecimal total,String currency,String status,Instant issuedAt,Instant dueAt,Instant paidAt,List<PaymentView> payments) {}
    public record TenantSubscriptions(SubscriptionView current,List<SubscriptionView> history,List<InvoiceView> invoices) {}
    private static final Duration DUE=Duration.ofDays(7);
    private final SubscriptionRepository subscriptions;private final SubscriptionInvoiceRepository invoices;private final SubscriptionPaymentRepository payments;
    private final PlanRepository plans;private final PlanVersionRepository versions;private final TenantLifecycleService tenants;private final ConfigurationService config;private final PlatformAuditService audit;private final Clock clock;
    public SubscriptionAdminService(SubscriptionRepository subscriptions,SubscriptionInvoiceRepository invoices,SubscriptionPaymentRepository payments,PlanRepository plans,PlanVersionRepository versions,TenantLifecycleService tenants,ConfigurationService config,PlatformAuditService audit,Clock clock){
        this.subscriptions=subscriptions;this.invoices=invoices;this.payments=payments;this.plans=plans;this.versions=versions;this.tenants=tenants;this.config=config;this.audit=audit;this.clock=clock;
    }

    static SubscriptionView view(TenantSubscription s){
        return new SubscriptionView(s.id(),s.tenant(),s.planCode(),s.planVersion(),Interval.valueOf(s.interval()),s.price(),s.currency(),s.taxRate(),s.taxInclusive(),s.graceDays(),s.maxUsers(),s.maxBranches(),s.maxProducts(),s.features().isBlank()?List.of():List.of(s.features().split(",")),State.valueOf(s.status()),s.statusReason(),s.periodStart(),s.periodEnd(),s.isEffective(),s.createdAt(),s.createdBy());
    }
    private InvoiceView view(SubscriptionInvoice i){
        var paid=payments.findByInvoiceIdOrderByRecordedAtAscIdAsc(i.id()).stream().map(p->new PaymentView(p.id(),p.amount(),p.currency(),p.method(),p.status(),p.merchantId(),p.reference(),p.recordedBy(),p.recordedAt())).toList();
        return new InvoiceView(i.id(),i.tenant(),i.subscriptionId(),i.number(),i.periodStart(),i.periodEnd(),i.amount(),i.tax(),i.total(),i.currency(),i.status(),i.issuedAt(),i.dueAt(),i.paidAt(),paid);
    }
    private static String reason(String r){
        if(r==null||r.isBlank())throw Problems.invalid("A reason is required");
        if(r.strip().length()>500)throw Problems.invalid("Reason must be at most 500 characters");
        return r.strip();
    }
    private void requireTenant(long tenantId,boolean allowClosed){
        var t=tenants.find(tenantId).orElseThrow(()->Problems.missing("Tenant not found"));
        if(!allowClosed&&t.status()==TenantStatus.CLOSED)throw Problems.conflict("A closed tenant cannot hold a subscription");
    }

    /** Assigns a plan version; the agreed terms are snapshotted. An existing ACTIVE/SUSPENDED subscription is ended as replaced. */
    public SubscriptionView assign(String actor,long tenantId,String planCode,Integer version){
        requireTenant(tenantId,false);
        var plan=plans.findById(planCode).orElseThrow(()->Problems.missing("Plan not found"));
        if(!plan.active())throw Problems.conflict("Plan is retired and cannot be assigned");
        var terms=(version==null?versions.findByPlanCodeOrderByVersionDesc(planCode).stream().findFirst():versions.findByPlanCodeAndVersion(planCode,version)).orElseThrow(()->Problems.missing("Plan version not found"));
        var current=subscriptions.lockEffective(tenantId);
        if(current.isPresent()){
            var c=current.get();var state=State.valueOf(c.status());
            if(!SubscriptionLifecycle.canTransition(state,State.CANCELLED))throw Problems.conflict("Current subscription is "+state+"; resolve it before assigning another plan");
            c.status(State.CANCELLED.name(),"Replaced by plan "+planCode+" v"+terms.version(),true);subscriptions.saveAndFlush(c);
        }
        var created=subscriptions.saveAndFlush(new TenantSubscription(UUID.randomUUID().toString(),tenantId,terms,clock.instant(),actor));
        audit.record(actor,"SUBSCRIPTION_ASSIGNED","SUBSCRIPTION",created.id(),tenantId,null,"plan="+planCode+" v"+terms.version()+" price="+terms.price()+" "+terms.currency());
        return view(created);
    }
    /** Adjusts the effective subscription along the SRS state machine. Does not change the tenant lifecycle (SUB-008). */
    public SubscriptionView transition(String actor,long tenantId,State target,String why){
        String note=reason(why);
        var sub=subscriptions.lockEffective(tenantId).orElseThrow(()->Problems.missing("Tenant has no effective subscription"));
        var from=State.valueOf(sub.status());
        if(!SubscriptionLifecycle.canTransition(from,target))throw Problems.conflict("Cannot move a subscription from "+from+" to "+target);
        if(target==State.ACTIVE&&sub.periodStart()==null){var now=clock.instant();sub.period(now,InvoiceMath.periodEnd(now,Interval.valueOf(sub.interval())));}
        sub.status(target.name(),note,SubscriptionLifecycle.terminal(target));
        subscriptions.saveAndFlush(sub);
        audit.record(actor,from==State.SUSPENDED&&target==State.ACTIVE?"SUBSCRIPTION_REACTIVATED":"SUBSCRIPTION_"+target.name(),"SUBSCRIPTION",sub.id(),tenantId,note,"from="+from+" to="+target);
        return view(sub);
    }
    /** Idempotent per subscription period: repeating returns the existing invoice. */
    public InvoiceView issueInvoice(String actor,long tenantId){
        var sub=subscriptions.lockEffective(tenantId).orElseThrow(()->Problems.missing("Tenant has no effective subscription"));
        var now=clock.instant();
        if(sub.periodStart()==null){sub.period(now,InvoiceMath.periodEnd(now,Interval.valueOf(sub.interval())));subscriptions.saveAndFlush(sub);}
        var existing=invoices.findBySubscriptionIdAndPeriodStart(sub.id(),sub.periodStart());
        if(existing.isPresent())return view(existing.get());
        var amounts=InvoiceMath.amounts(sub.price(),sub.taxRate(),sub.taxInclusive());
        var number="SUB-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase();
        var invoice=invoices.saveAndFlush(new SubscriptionInvoice(UUID.randomUUID().toString(),tenantId,sub.id(),number,sub.periodStart(),sub.periodEnd(),amounts.amount(),amounts.tax(),amounts.total(),sub.currency(),now,now.plus(DUE)));
        audit.record(actor,"SUBSCRIPTION_INVOICE_ISSUED","SUBSCRIPTION_INVOICE",invoice.id(),tenantId,null,"number="+number+" total="+amounts.total()+" "+sub.currency());
        return view(invoice);
    }
    /** Records a payment into the platform merchant account. Customer purchase payments are a different system and never appear here. */
    public InvoiceView recordPayment(String actor,String invoiceId,BigDecimal amount,String method,String reference,boolean succeeded){
        if(method==null||!List.of("OFFLINE","GATEWAY").contains(method))throw Problems.invalid("Method must be OFFLINE or GATEWAY");
        if(reference!=null&&reference.strip().length()>120)throw Problems.invalid("Reference is too long");
        if(method.equals("GATEWAY")&&(reference==null||reference.isBlank()))throw Problems.invalid("A provider reference is required for gateway payments");
        var invoice=invoices.lockById(invoiceId).orElseThrow(()->Problems.missing("Invoice not found"));
        if(!invoice.status().equals("OPEN"))throw Problems.conflict("Invoice is "+invoice.status());
        if(invoice.total().signum()==0)throw Problems.conflict("Nothing is payable on a zero-value invoice");
        if(amount==null||amount.compareTo(invoice.total())!=0)throw Problems.invalid("Payment must equal the invoice total of "+invoice.total().toPlainString());
        String merchant=config.raw(Key.SUBSCRIPTION_MERCHANT_ID).orElseThrow(()->Problems.conflict("Platform merchant account is not configured"));
        var now=clock.instant();
        try{payments.saveAndFlush(new SubscriptionPayment(UUID.randomUUID().toString(),invoice.id(),invoice.tenant(),amount,invoice.currency(),method,succeeded?"SUCCEEDED":"FAILED",merchant,reference==null||reference.isBlank()?null:reference.strip(),actor,now));}
        catch(DataIntegrityViolationException e){throw Problems.conflict("Provider reference was already recorded");}
        var sub=subscriptions.findById(invoice.subscriptionId()).orElseThrow(()->Problems.missing("Subscription not found"));
        String action="SUBSCRIPTION_PAYMENT_FAILED";
        if(succeeded){
            invoice.paid(now);invoices.saveAndFlush(invoice);action="SUBSCRIPTION_PAYMENT_RECORDED";
            var state=State.valueOf(sub.status());
            if(sub.isEffective()&&SubscriptionLifecycle.canTransition(state,State.ACTIVE)){sub.status(State.ACTIVE.name(),"Payment received for "+invoice.number(),false);if(state==State.SUSPENDED)action="SUBSCRIPTION_REACTIVATED";}
            if(sub.isEffective()&&State.valueOf(sub.status())==State.ACTIVE)sub.period(invoice.periodStart(),invoice.periodEnd());
            subscriptions.saveAndFlush(sub);
        }else if(sub.isEffective()&&SubscriptionLifecycle.canTransition(State.valueOf(sub.status()),State.PAST_DUE)){
            sub.status(State.PAST_DUE.name(),"Payment failed for "+invoice.number(),false);subscriptions.saveAndFlush(sub);
        }
        audit.record(actor,action,"SUBSCRIPTION_INVOICE",invoice.id(),invoice.tenant(),null,"number="+invoice.number()+" amount="+amount.toPlainString()+" "+invoice.currency()+" method="+method);
        return view(invoice);
    }
    @Transactional(readOnly=true) public TenantSubscriptions forTenant(long tenantId){
        requireTenant(tenantId,true);
        var history=new ArrayList<SubscriptionView>();for(var s:subscriptions.findByTenantOrderByCreatedAtDescIdDesc(tenantId))history.add(view(s));
        var current=subscriptions.findEffective(tenantId).map(SubscriptionAdminService::view).orElse(null);
        return new TenantSubscriptions(current,history,invoices.findByTenantOrderByIssuedAtDescIdDesc(tenantId).stream().map(this::view).toList());
    }
}
