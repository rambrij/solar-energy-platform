package com.saumya.pos.subscription.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

    boolean existsByTenantIdAndStatusIn(long tenantId,Collection<String> statuses);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDescIdDesc(long tenantId);
    List<TenantSubscription> findAllByOrderByCreatedAtDescIdDesc();
    Optional<TenantSubscription> findByIdAndTenantId(long id,long tenantId);
}
