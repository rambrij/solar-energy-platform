package com.saumya.pos.subscription;

import com.saumya.pos.subscription.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.tenant.TenantStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in local/test simulation. No gateway or statutory invoice is produced. */
@Component
class MockSubscriptionBillingJob {
    private static final String ACTOR = "system:subscription-mock-billing";
    private static final String PROVIDER = "MOCK_SUBSCRIPTION";
    private static final String INVOICE_PREFIX = "MOCK-SUB-";
    private final TenantSubscriptionRepository subscriptions;
    private final SubscriptionInvoiceRepository invoices;
    private final SubscriptionPaymentRepository payments;
    private final SubscriptionPlanVersionRepository versions;
    private final SubscriptionEventRepository events;
    private final TenantLifecycleService tenants;
    private final MockSubscriptionBillingReminder reminder;
    private final Clock clock;
    private final boolean enabled;
    private final boolean successful;
    private final int maxAttempts;
    private final long retrySeconds;

    MockSubscriptionBillingJob(TenantSubscriptionRepository subscriptions, SubscriptionInvoiceRepository invoices,
        SubscriptionPaymentRepository payments, SubscriptionPlanVersionRepository versions,
        SubscriptionEventRepository events, TenantLifecycleService tenants, MockSubscriptionBillingReminder reminder,
        Clock clock, Environment environment,
        @Value("${subscriptions.mock.enabled:false}") boolean enabled,
        @Value("${subscriptions.mock.outcome:}") String outcome,
        @Value("${subscriptions.mock.max-attempts:0}") int maxAttempts,
        @Value("${subscriptions.mock.retry-seconds:0}") long retrySeconds) {
        if(enabled&&!environment.acceptsProfiles(Profiles.of("(local | test) & !prod & !production")))
            throw new IllegalStateException("Mock subscription billing requires a local or test profile");
        if(enabled&&(!("SUCCESS".equals(outcome)||"FAIL".equals(outcome))
            ||maxAttempts<1||maxAttempts>8||retrySeconds<1||retrySeconds>3600))
            throw new IllegalStateException("Configure a mock billing outcome, 1–8 attempts and 1–3600 retry seconds");
        this.subscriptions=subscriptions;
        this.invoices=invoices;
        this.payments=payments;
        this.versions=versions;
        this.events=events;
        this.tenants=tenants;
        this.reminder=reminder;
        this.clock=clock;
        this.enabled=enabled;
        this.successful="SUCCESS".equals(outcome);
        this.maxAttempts=maxAttempts;
        this.retrySeconds=retrySeconds;
    }

    @Scheduled(fixedDelayString="${subscriptions.mock.poll-ms:60000}")
    @Transactional
    public void runDueBilling(){
        if(!enabled)return;
        Instant now=clock.instant();
        var today=LocalDate.ofInstant(now,ZoneOffset.UTC);
        var due=subscriptions.lockMockBillingCandidates(TenantStatus.ACTIVE,today,now,PageRequest.of(0,50));
        for(var subscription:due){
            if(tenants.lockActive(subscription.tenantId())) process(subscription.tenantId(),subscription,today,now);
        }
    }

    private void process(long tenantId,TenantSubscription subscription, LocalDate today, Instant now){
        long subscriptionId=subscription.id();
        var existing=invoices.findFirstByTenantIdAndSubscriptionIdAndStatusAndMockNextAttemptAtLessThanEqualOrderByIdAsc(
            tenantId,subscriptionId,"OPEN",now);
        boolean initial="PENDING".equals(subscription.status());
        LocalDate start=initial?subscription.periodStart():subscription.periodEnd();
        if(existing.isEmpty()&&((!initial&&!"ACTIVE".equals(subscription.status()))||start.isAfter(today)
            ||invoices.existsByTenantIdAndSubscriptionIdAndPeriodStart(tenantId,subscriptionId,start)))return;

        var key=new SubscriptionPlanVersion.Key();
        key.planId=subscription.planId();
        key.version=subscription.planVersion();
        var plan=versions.findById(key).orElseThrow();
        var invoice=existing.orElseGet(()->issue(subscription,plan,start,initial,now));
        if(!invoice.invoiceNumber().startsWith(INVOICE_PREFIX))return;
        long attempt=payments.countByTenantIdAndInvoiceIdAndProvider(tenantId,invoice.id(),PROVIDER)+1;
        if(attempt>maxAttempts){invoice.scheduleMockRetry(null);return;}

        String attemptKey="mock-sub-"+invoice.id()+"-"+attempt;
        payments.save(new SubscriptionPaymentRecord(tenantId,invoice.id(),attemptKey,invoice.total(),
            invoice.currency(),successful,now));
        String prior=subscription.status();
        if(successful){
            invoice.markMockPaid();
            subscription.renewMockPeriod(invoice.periodStart(),invoice.periodEnd(),now);
            event(subscription,prior,"ACTIVE","Mock payment confirmed for "+invoice.invoiceNumber(),now);
        }else{
            invoice.scheduleMockRetry(attempt==maxAttempts?null:now.plusSeconds(retrySeconds*(1L<<(attempt-1))));
            if("ACTIVE".equals(prior)||"PENDING".equals(prior)){
                subscription.transition("PAST_DUE",now,0);
                event(subscription,prior,"PAST_DUE","Mock payment failed for "+invoice.invoiceNumber(),now);
                subscription.transition("GRACE_PERIOD",now,plan.graceDays());
                event(subscription,"PAST_DUE","GRACE_PERIOD","Grace started for "+invoice.invoiceNumber(),now);
                var tenant=tenants.get(tenantId);
                reminder.afterCommit(tenant.contactEmail()==null?tenant.initialVendorAdminUsername():tenant.contactEmail(),
                    invoice.invoiceNumber(),subscription.graceUntil());
            }
        }
    }

    private SubscriptionInvoiceRecord issue(TenantSubscription subscription, SubscriptionPlanVersion plan,
                                            LocalDate start,boolean initial,Instant now){
        LocalDate end=initial?subscription.periodEnd():BillingPeriod.renewalEnd(start,subscription.billingAnchor(),plan.billingInterval());
        return invoices.saveAndFlush(new SubscriptionInvoiceRecord(subscription.tenantId(),subscription.id(),
            INVOICE_PREFIX+subscription.id()+"-"+start,start,end,plan.currency(),subscription.termsSnapshot(),plan.price(),now));
    }

    private void event(TenantSubscription subscription,String prior,String next,String reason,Instant now){
        events.save(new SubscriptionEvent(subscription.tenantId(),subscription.id(),prior,next,ACTOR,reason,now));
    }
}
