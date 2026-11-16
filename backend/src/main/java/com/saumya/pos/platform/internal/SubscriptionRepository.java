package com.saumya.pos.platform.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<TenantSubscription,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from TenantSubscription s where s.tenant=:tenant and s.effective=1")
    Optional<TenantSubscription> lockEffective(@Param("tenant") long tenant);
    @Query("select s from TenantSubscription s where s.tenant=:tenant and s.effective=1") Optional<TenantSubscription> findEffective(@Param("tenant") long tenant);
    List<TenantSubscription> findByTenantOrderByCreatedAtDescIdDesc(long tenant);
    @Query("select s.status, count(s) from TenantSubscription s where s.effective=1 group by s.status") List<Object[]> countEffectiveByStatus();
    @Query("select s.planCode, count(s) from TenantSubscription s where s.effective=1 group by s.planCode") List<Object[]> countEffectiveByPlan();
}
