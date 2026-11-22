package com.saumya.pos.subscription.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanVersionRepository extends JpaRepository<SubscriptionPlanVersion,SubscriptionPlanVersion.Key> {
    List<SubscriptionPlanVersion> findByPlanIdOrderByVersionDesc(long planId);
}
