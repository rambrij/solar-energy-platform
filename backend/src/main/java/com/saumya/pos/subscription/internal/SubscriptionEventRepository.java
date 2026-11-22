package com.saumya.pos.subscription.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent,Long> {
    List<SubscriptionEvent> findBySubscriptionIdOrderByOccurredAtDescIdDesc(long subscriptionId);
}
