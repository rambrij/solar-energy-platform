package com.saumya.pos.subscription;

import com.saumya.pos.subscription.internal.SubscriptionEvent;
import com.saumya.pos.subscription.internal.SubscriptionEventRepository;
import com.saumya.pos.subscription.internal.TenantSubscriptionRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Processes only persisted subscriptions whose configured grace deadline has passed. */
@Component
class SubscriptionGraceExpiryJob {
    private static final String SYSTEM_ACTOR = "system:subscription-grace-expiry";

    private final TenantSubscriptionRepository subscriptions;
    private final SubscriptionEventRepository events;
    private final Clock clock;

    SubscriptionGraceExpiryJob(TenantSubscriptionRepository subscriptions,
                               SubscriptionEventRepository events, Clock clock) {
        this.subscriptions = subscriptions;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${subscriptions.grace-expiry-delay-ms:60000}")
    @Transactional
    public void expireGrace() {
        var now = clock.instant();
        var due = subscriptions.findTop100ByStatusAndGraceUntilLessThanEqualOrderByGraceUntilAscIdAsc(
            "GRACE_PERIOD", now);
        for (var subscription : due) {
            if (!subscription.expireGraceAt(now)) continue;
            events.save(new SubscriptionEvent(
                subscription.tenantId(), subscription.id(), "GRACE_PERIOD", "SUSPENDED",
                SYSTEM_ACTOR, "Subscription grace period expired without payment", now));
        }
    }
}
