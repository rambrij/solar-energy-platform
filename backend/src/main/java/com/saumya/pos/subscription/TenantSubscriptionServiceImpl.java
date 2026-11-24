package com.saumya.pos.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.security.PlatformAuthorization;
import com.saumya.pos.subscription.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class TenantSubscriptionServiceImpl implements TenantSubscriptionService {
    private static final List<String> EFFECTIVE=List.of("PENDING","ACTIVE","PAST_DUE","GRACE_PERIOD","SUSPENDED","CANCEL_AT_PERIOD_END");
    private final TenantSubscriptionRepository subscriptions;private final SubscriptionPlanRepository plans;
    private final SubscriptionPlanVersionRepository versions;private final SubscriptionEventRepository events;
    private final SubscriptionInvoiceRepository invoices;private final SubscriptionPaymentRepository payments;
    private final TenantLifecycleService tenants;private final ObjectMapper json;private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.security.AuthorizationContext authorization;
    private void requirePlatform(String actor,String permission){
        if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Platform authorization context unavailable");
        var context=authorization.current();
        if(!context.isPlatformUser()||!context.username().equals(actor))throw new ResponseStatusException(FORBIDDEN,"Platform actor does not match authenticated identity");
        context.requirePermission(permission);
    }
    private void requireRead(){
        if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Platform authorization context unavailable");
        var context=authorization.current();
        if(!context.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");
        context.requirePermission("PLATFORM_SUBSCRIPTION_VIEW");
    }
    public TenantSubscriptionServiceImpl(TenantSubscriptionRepository subscriptions,SubscriptionPlanRepository plans,
        SubscriptionPlanVersionRepository versions,SubscriptionEventRepository events,SubscriptionInvoiceRepository invoices,
        SubscriptionPaymentRepository payments,TenantLifecycleService tenants,ObjectMapper json,Clock clock){
        this.subscriptions=subscriptions;this.plans=plans;this.versions=versions;this.events=events;this.invoices=invoices;this.payments=payments;this.tenants=tenants;this.json=json;this.clock=clock;
    }
    @Override public SubscriptionView create(SubscriptionInput input,String actor){
        requirePlatform(actor,"PLATFORM_SUBSCRIPTION_ADJUST");
        var tenant=tenants.get(input.tenantId());
        if("CLOSED".equals(tenant.lifecycleStatus()))throw new ResponseStatusException(CONFLICT,"Closed tenants cannot receive subscriptions");
        if(subscriptions.existsByTenantIdAndStatusIn(input.tenantId(),EFFECTIVE))throw new ResponseStatusException(CONFLICT,"Tenant already has an effective subscription");
        var plan=plans.findById(input.planId()).filter(SubscriptionPlan::active).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Active subscription plan unavailable"));
        if(input.expectedPlanVersion()!=null&&input.expectedPlanVersion()!=plan.currentVersion())
            throw new ResponseStatusException(CONFLICT,"Subscription plan changed. Reload before assigning it.");
        var version=planVersion(plan.id(),plan.currentVersion());var snapshot=snapshot(version);var now=clock.instant();
        var periodEnd=BillingPeriod.end(input.periodStart(),input.periodEnd(),input.usePlanInterval(),version.billingInterval());
        String reason=required(input.reason());
        var entity=subscriptions.saveAndFlush(new TenantSubscription(input.tenantId(),plan.id(),version.version(),input.periodStart(),periodEnd,snapshot,now));
        events.saveAndFlush(new SubscriptionEvent(input.tenantId(),entity.id(),null,"PENDING",actor,reason,now));
        return view(entity);
    }
    @Override public SubscriptionView transition(long id,String target,String reason,String actor,Long expectedVersion){
        requirePlatform(actor,"PLATFORM_SUBSCRIPTION_ADJUST");
        var entity=subscriptions.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Tenant subscription unavailable"));
        if(expectedVersion!=null&&entity.version()!=expectedVersion)
            throw new ResponseStatusException(CONFLICT,"Subscription changed. Reload before applying a transition.");
        String old=entity.status(),next=target==null?"":target.strip().toUpperCase(Locale.ROOT);
        if("ACTIVE".equals(next))throw new ResponseStatusException(CONFLICT,"Subscription reactivation requires a verified platform payment event; manual status changes cannot mark an invoice paid");
        if(!administrativeTransitions(old).contains(next))throw new ResponseStatusException(CONFLICT,"Invalid subscription transition: "+old+" -> "+next);
        int grace=planVersion(entity.planId(),entity.planVersion()).graceDays();var now=clock.instant();
        entity.transition(next,now,grace);events.saveAndFlush(new SubscriptionEvent(entity.tenantId(),entity.id(),old,next,actor,required(reason),now));
        return view(entity);
    }
    @Override @Transactional(readOnly=true) public List<SubscriptionView> forTenant(long tenantId){requireRead();tenants.get(tenantId);return subscriptions.findByTenantIdOrderByCreatedAtDescIdDesc(tenantId).stream().map(this::view).toList();}
    @Override @Transactional(readOnly=true) public List<SubscriptionView> all(){requireRead();return subscriptions.findAllByOrderByCreatedAtDescIdDesc().stream().map(this::view).toList();}
    @Override
    @Transactional(readOnly=true)
    public List<StatusCount> statusCounts(){
        PlatformAuthorization.require("PLATFORM_ANALYTICS_VIEW",null);
        return subscriptions.countGroupedByStatus().stream()
            .map(total->new StatusCount(total.getStatus(),total.getTotal()))
            .toList();
    }
    @Override @Transactional(readOnly=true) public List<SubscriptionEventView> events(long subscriptionId){
        requireRead();
        subscriptions.findById(subscriptionId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Tenant subscription unavailable"));
        return events.findBySubscriptionIdOrderByOccurredAtDescIdDesc(subscriptionId).stream().map(e->new SubscriptionEventView(e.id(),e.oldStatus(),e.newStatus(),e.actor(),e.reason(),e.occurredAt())).toList();
    }
    @Override @Transactional(readOnly=true) public BillingHistory billingHistory(long subscriptionId){
        requireRead();
        subscriptions.findById(subscriptionId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Tenant subscription unavailable"));
        var rows=invoices.findBySubscriptionIdOrderByIssuedAtDescIdDesc(subscriptionId);
        var invoiceViews=rows.stream().map(i->new InvoiceView(i.id(),i.tenantId(),i.subscriptionId(),i.invoiceNumber(),i.periodStart(),i.periodEnd(),i.currency(),i.status(),i.subtotal(),i.taxAmount(),i.total(),i.dueAt(),i.issuedAt())).toList();
        var paymentViews=rows.stream().flatMap(i->payments.findByInvoiceIdOrderByCreatedAtDescIdDesc(i.id()).stream())
            .map(p->new PaymentView(p.id(),p.tenantId(),p.invoiceId(),p.provider(),p.providerPaymentReference(),p.amount(),p.currency(),p.status(),p.createdAt(),p.confirmedAt())).toList();
        return new BillingHistory(invoiceViews,paymentViews);
    }
    private SubscriptionPlanVersion planVersion(long planId,int version){var key=new SubscriptionPlanVersion.Key();key.planId=planId;key.version=version;return versions.findById(key).orElseThrow(()->new ResponseStatusException(CONFLICT,"Historical subscription plan version unavailable"));}
    private SubscriptionView view(TenantSubscription s){var tenant=tenants.get(s.tenantId());var p=plans.findById(s.planId()).orElse(null);return new SubscriptionView(s.id(),s.tenantId(),tenant.name(),s.planId(),p==null?"":p.planKey(),p==null?"":p.displayName(),s.planVersion(),s.status(),s.periodStart(),s.periodEnd(),s.graceUntil(),s.termsSnapshot(),s.createdAt(),s.version(),administrativeTransitions(s.status()));}
    private String snapshot(SubscriptionPlanVersion v){
        try{
            Map<String,Object> values=new LinkedHashMap<>();values.put("planVersion",v.version());values.put("price",v.price());values.put("currency",v.currency());values.put("billingInterval",v.billingInterval());values.put("maxUsers",v.maxUsers());values.put("maxStorageMb",v.maxStorageMb());values.put("maxBranches",v.maxBranches());values.put("maxWarehouses",v.maxWarehouses());values.put("entitlements",json.readTree(v.entitlements()));values.put("graceDays",v.graceDays());values.put("taxMetadata",json.readTree(v.taxMetadata()));values.put("invoiceDescription",v.invoiceDescription());return json.writeValueAsString(values);
        }catch(Exception e){throw new IllegalStateException("Plan snapshot could not be serialized",e);}
    }
    private static List<String> administrativeTransitions(String status){return switch(status){
        case "PENDING"->List.of("PAST_DUE","CANCELLED");
        case "ACTIVE"->List.of("PAST_DUE","CANCEL_AT_PERIOD_END","CANCELLED","EXPIRED","SUSPENDED");
        case "PAST_DUE"->List.of("GRACE_PERIOD","SUSPENDED","CANCELLED");
        case "GRACE_PERIOD"->List.of("SUSPENDED","CANCELLED");
        case "SUSPENDED"->List.of("CANCELLED");
        case "CANCEL_AT_PERIOD_END"->List.of("CANCELLED","EXPIRED");
        default->List.of();
    };}
    private static String required(String value){if(value==null||value.isBlank()||value.strip().length()>500)throw new ResponseStatusException(BAD_REQUEST,"A subscription change reason is required");return value.strip();}
}
