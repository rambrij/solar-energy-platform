package com.saumya.pos.subscription.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan,Long> {
    Optional<SubscriptionPlan> findByPlanKey(String planKey);
}
