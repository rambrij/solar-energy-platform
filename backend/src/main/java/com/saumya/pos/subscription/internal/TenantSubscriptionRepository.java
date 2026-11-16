package com.saumya.pos.subscription.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription,Long> {
    boolean existsByTenantIdAndStatusIn(long tenantId,Collection<String> statuses);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDescIdDesc(long tenantId);
    List<TenantSubscription> findAllByOrderByCreatedAtDescIdDesc();
    Optional<TenantSubscription> findByIdAndTenantId(long id,long tenantId);
}
