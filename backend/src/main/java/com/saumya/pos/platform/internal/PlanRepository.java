package com.saumya.pos.platform.internal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanRepository extends JpaRepository<SubscriptionPlan,String> { List<SubscriptionPlan> findAllByOrderByCodeAsc(); }
