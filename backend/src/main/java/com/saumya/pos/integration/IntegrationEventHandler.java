package com.saumya.pos.integration;

import java.util.Set;

/**
 * A synchronous, idempotent consumer of committed outbox events.
 * Implementations must use the event ID as their downstream idempotency key because
 * a delivery may be repeated after a process crash or an expired lease.
 */
public interface IntegrationEventHandler {
    Set<String> eventTypes();

    void handle(IntegrationOutboxService.Event event) throws Exception;
}
