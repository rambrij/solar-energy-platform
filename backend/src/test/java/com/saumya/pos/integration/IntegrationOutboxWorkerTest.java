package com.saumya.pos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegrationOutboxWorkerTest {
    private static IntegrationOutboxService.Event event(String id, String type) {
        return new IntegrationOutboxService.Event(
            id, 42L, "vendor-admin", "Lead", "17", type, 1, "corr-1", "{}", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void dispatchesOnlyRegisteredEventTypesAndAcknowledgesAfterAllHandlersReturn() throws Exception {
        IntegrationOutboxService outbox = mock(IntegrationOutboxService.class);
        IntegrationEventHandler first = mock(IntegrationEventHandler.class);
        IntegrationEventHandler second = mock(IntegrationEventHandler.class);
        when(first.eventTypes()).thenReturn(Set.of("LEAD_CREATED"));
        when(second.eventTypes()).thenReturn(Set.of("LEAD_CREATED"));
        var message = event("e-1", "LEAD_CREATED");
        when(outbox.claimForTypes(eq("worker-1"), eq(25), any(Duration.class), eq(Set.of("LEAD_CREATED"))))
            .thenReturn(List.of(message));

        var worker = new IntegrationOutboxWorker(outbox, List.of(first, second), "worker-1", 25, 120);
        assertThat(worker.dispatchOnce()).isEqualTo(1);

        verify(first).handle(message);
        verify(second).handle(message);
        verify(outbox).published("e-1", "worker-1");
        verify(outbox, never()).failed(eq("e-1"), eq("worker-1"), any());
    }

    @Test
    void failedHandlerSchedulesRetryAndDoesNotAcknowledgeEvent() throws Exception {
        IntegrationOutboxService outbox = mock(IntegrationOutboxService.class);
        IntegrationEventHandler handler = mock(IntegrationEventHandler.class);
        when(handler.eventTypes()).thenReturn(Set.of("LEAD_CREATED"));
        var message = event("e-2", "LEAD_CREATED");
        when(outbox.claimForTypes(eq("worker-2"), eq(25), any(Duration.class), eq(Set.of("LEAD_CREATED"))))
            .thenReturn(List.of(message));
        doThrow(new IllegalStateException("provider unavailable")).when(handler).handle(message);

        var worker = new IntegrationOutboxWorker(outbox, List.of(handler), "worker-2", 25, 120);
        assertThat(worker.dispatchOnce()).isZero();

        verify(outbox).failed(eq("e-2"), eq("worker-2"), any(IllegalStateException.class));
        verify(outbox, never()).published("e-2", "worker-2");
    }

    @Test
    void leavesEventsPendingWhenNoHandlerIsInstalled() {
        IntegrationOutboxService outbox = mock(IntegrationOutboxService.class);
        var worker = new IntegrationOutboxWorker(outbox, List.of(), "worker-3", 25, 120);

        assertThat(worker.dispatchOnce()).isZero();
        verify(outbox, never()).claimForTypes(any(), any(Integer.class), any(), any());
    }
}
