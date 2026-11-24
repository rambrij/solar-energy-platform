package com.saumya.pos.subscription;

import com.saumya.pos.subscription.internal.SubscriptionEvent;
import com.saumya.pos.subscription.internal.SubscriptionEventRepository;
import com.saumya.pos.subscription.internal.TenantSubscription;
import com.saumya.pos.subscription.internal.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SubscriptionGraceExpiryJobTest {
    @Test void suspendsDueSubscriptionsOnceAndKeepsAnAttributedHistoryEvent() {
        var now = Instant.parse("2026-11-24T00:00:00Z");
        var due = subscription(11, now);
        var future = subscription(12, now);
        due.transition("GRACE_PERIOD", now.minusSeconds(86_400), 1);
        future.transition("GRACE_PERIOD", now, 1);
        var subscriptions = mock(TenantSubscriptionRepository.class);
        var events = mock(SubscriptionEventRepository.class);
        when(subscriptions.findTop100ByStatusAndGraceUntilLessThanEqualOrderByGraceUntilAscIdAsc(
            "GRACE_PERIOD", now)).thenReturn(List.of(due, future));

        var job = new SubscriptionGraceExpiryJob(subscriptions, events, Clock.fixed(now, ZoneOffset.UTC));
        job.expireGrace();
        job.expireGrace();

        assertThat(due.status()).isEqualTo("SUSPENDED");
        assertThat(future.status()).isEqualTo("GRACE_PERIOD");
        var saved = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().tenantId()).isEqualTo(7);
        assertThat(saved.getValue().subscriptionId()).isEqualTo(11);
        assertThat(saved.getValue().oldStatus()).isEqualTo("GRACE_PERIOD");
        assertThat(saved.getValue().newStatus()).isEqualTo("SUSPENDED");
        assertThat(saved.getValue().actor()).isEqualTo("system:subscription-grace-expiry");
    }

    private static TenantSubscription subscription(long id, Instant now) {
        var subscription = new TenantSubscription(7, 1, 1, LocalDate.parse("2026-11-01"),
            LocalDate.parse("2026-12-01"), "{}", now);
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }
}
