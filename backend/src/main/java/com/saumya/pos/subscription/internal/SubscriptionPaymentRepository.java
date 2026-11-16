package com.saumya.pos.subscription.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPaymentRecord,Long> {
    List<SubscriptionPaymentRecord> findByTenantIdOrderByCreatedAtDescIdDesc(long tenantId);
    List<SubscriptionPaymentRecord> findByInvoiceIdOrderByCreatedAtDescIdDesc(long invoiceId);
}
