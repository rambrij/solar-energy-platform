package com.saumya.pos.subscription.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription,Long> {
    interface StatusTotal {
        String getStatus();
        long getTotal();
    }

    @Query("select s.status as status, count(s) as total from TenantSubscription s group by s.status order by s.status")
    List<StatusTotal> countGroupedByStatus();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TenantSubscription> findTop100ByStatusAndGraceUntilLessThanEqualOrderByGraceUntilAscIdAsc(
        String status, Instant deadline);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s from TenantSubscription s
        where exists (select t.id from Tenant t where t.id=s.tenantId and t.lifecycleStatus=:activeTenant)
          and ((((s.status='ACTIVE' and s.periodEnd<=:today) or (s.status='PENDING' and s.periodStart<=:today))
              and not exists (
                select i.id from SubscriptionInvoiceRecord i where i.tenantId=s.tenantId
                  and i.subscriptionId=s.id and i.periodStart=
                    case when s.status='PENDING' then s.periodStart else s.periodEnd end))
            or (s.status in ('ACTIVE','PAST_DUE','GRACE_PERIOD','SUSPENDED') and exists (
                select i.id from SubscriptionInvoiceRecord i where i.tenantId=s.tenantId
                  and i.subscriptionId=s.id and i.status='OPEN' and i.mockNextAttemptAt<=:now)))
        order by s.periodEnd,s.id
        """)
    List<TenantSubscription> lockMockBillingCandidates(
        com.saumya.pos.tenant.TenantStatus activeTenant, LocalDate today, Instant now, Pageable limit);

    boolean existsByTenantIdAndStatusIn(long tenantId,Collection<String> statuses);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDescIdDesc(long tenantId);
    List<TenantSubscription> findAllByOrderByCreatedAtDescIdDesc();
    Optional<TenantSubscription> findByIdAndTenantId(long id,long tenantId);
}
