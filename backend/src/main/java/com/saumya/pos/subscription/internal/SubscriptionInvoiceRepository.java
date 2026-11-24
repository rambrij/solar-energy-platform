package com.saumya.pos.subscription.internal;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoiceRecord,Long> {
    List<SubscriptionInvoiceRecord> findByTenantIdOrderByIssuedAtDescIdDesc(long tenantId);
    List<SubscriptionInvoiceRecord> findBySubscriptionIdOrderByIssuedAtDescIdDesc(long subscriptionId);
    Optional<SubscriptionInvoiceRecord> findByIdAndSubscriptionIdAndTenantId(long id,long subscriptionId,long tenantId);
    boolean existsByTenantIdAndSubscriptionIdAndPeriodStart(long tenantId,long subscriptionId,LocalDate periodStart);
    Optional<SubscriptionInvoiceRecord> findFirstByTenantIdAndSubscriptionIdAndStatusAndMockNextAttemptAtLessThanEqualOrderByIdAsc(
        long tenantId,long subscriptionId,String status,Instant now);
}
