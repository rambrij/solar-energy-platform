package com.saumya.pos.platform.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice,String> {
    Optional<SubscriptionInvoice> findBySubscriptionIdAndPeriodStart(String subscriptionId,Instant periodStart);
    List<SubscriptionInvoice> findByTenantOrderByIssuedAtDescIdDesc(long tenant);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from SubscriptionInvoice i where i.id=:id") Optional<SubscriptionInvoice> lockById(@Param("id") String id);
    @Query("select i.currency, count(i), coalesce(sum(i.total),0) from SubscriptionInvoice i where i.status='OPEN' group by i.currency") List<Object[]> openTotalsByCurrency();
}
