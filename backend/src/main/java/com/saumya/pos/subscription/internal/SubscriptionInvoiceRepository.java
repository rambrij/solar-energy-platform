package com.saumya.pos.subscription.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoiceRecord,Long> {
    List<SubscriptionInvoiceRecord> findByTenantIdOrderByIssuedAtDescIdDesc(long tenantId);
    List<SubscriptionInvoiceRecord> findBySubscriptionIdOrderByIssuedAtDescIdDesc(long subscriptionId);
}
