package com.saumya.pos.subscription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface TenantSubscriptionService {
    record SubscriptionInput(long tenantId,long planId,LocalDate periodStart,LocalDate periodEnd,String reason,
        boolean usePlanInterval,Integer expectedPlanVersion) {}
    record SubscriptionView(long id,long tenantId,String tenantName,long planId,String planKey,String planName,int planVersion,
        String status,LocalDate periodStart,LocalDate periodEnd,Instant graceUntil,String termsSnapshot,Instant createdAt,long version,
        List<String> allowedAdministrativeTransitions) {}
    record SubscriptionEventView(long id,String oldStatus,String newStatus,String actor,String reason,Instant occurredAt) {}
    record InvoiceView(long id,long tenantId,long subscriptionId,String invoiceNumber,LocalDate periodStart,LocalDate periodEnd,
        String currency,String status,java.math.BigDecimal subtotal,java.math.BigDecimal taxAmount,java.math.BigDecimal total,Instant dueAt,Instant issuedAt,
        Instant mockNextAttemptAt) {}
    record PaymentView(long id,long tenantId,long invoiceId,String provider,String providerPaymentReference,
        java.math.BigDecimal amount,String currency,String status,Instant createdAt,Instant confirmedAt) {}
    record BillingHistory(List<InvoiceView> invoices,List<PaymentView> payments) {}
    record StatusCount(String status,long count) {}
    SubscriptionView create(SubscriptionInput input,String actor);
    SubscriptionView transition(long subscriptionId,String nextStatus,String reason,String actor,Long expectedVersion);
    List<SubscriptionView> forTenant(long tenantId);
    List<SubscriptionView> all();
    List<StatusCount> statusCounts();
    List<SubscriptionEventView> events(long subscriptionId);
    BillingHistory billingHistory(long subscriptionId);
    InvoiceView mockInvoice(long subscriptionId,long invoiceId);
}
