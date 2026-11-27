package com.saumya.pos.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Leased, at-least-once outbox dispatcher. */
@Component
public class IntegrationOutboxWorker {
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z0-9_.-]{1,119}");

    private final IntegrationOutboxService outbox;
    private final Map<String, List<IntegrationEventHandler>> handlersByType;
    private final String workerId;
    private final int batchSize;
    private final Duration lease;

    public IntegrationOutboxWorker(
            IntegrationOutboxService outbox,
            List<IntegrationEventHandler> handlers,
            @Value("${integration.outbox.worker-id:}") String configuredWorkerId,
            @Value("${integration.outbox.batch-size:25}") int batchSize,
            @Value("${integration.outbox.lease-seconds:120}") long leaseSeconds) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Outbox batch size must be 1..100");
        }
        if (leaseSeconds < 1 || leaseSeconds > 600) {
            throw new IllegalArgumentException("Outbox lease must be 1..600 seconds");
        }
        this.outbox = outbox;
        this.batchSize = batchSize;
        this.lease = Duration.ofSeconds(leaseSeconds);
        this.workerId = StringUtils.hasText(configuredWorkerId)
            ? configuredWorkerId.strip()
            : UUID.randomUUID().toString();
        if (this.workerId.length() > 36) {
            throw new IllegalArgumentException("Outbox worker identity must be at most 36 characters");
        }

        Map<String, List<IntegrationEventHandler>> routes = new LinkedHashMap<>();
        for (IntegrationEventHandler handler : handlers) {
            Set<String> types = handler.eventTypes();
            if (types == null || types.isEmpty()) {
                throw new IllegalStateException("Every outbox handler must declare at least one event type");
            }
            for (String type : types) {
                if (type == null || !EVENT_TYPE.matcher(type).matches()) {
                    throw new IllegalStateException("Outbox handler declared an invalid event type");
                }
                routes.computeIfAbsent(type, ignored -> new ArrayList<>()).add(handler);
            }
        }
        this.handlersByType = routes.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    @Scheduled(
        fixedDelayString = "${integration.outbox.poll-delay-ms:1000}",
        initialDelayString = "${integration.outbox.initial-delay-ms:1000}")
    public void scheduledDispatch() {
        dispatchOnce();
    }

    /** Dispatches one bounded batch; exposed for deterministic tests and controlled operations. */
    public int dispatchOnce() {
        if (handlersByType.isEmpty()) {
            return 0;
        }
        Set<String> eventTypes = handlersByType.keySet();
        List<IntegrationOutboxService.Event> claimed =
            outbox.claimForTypes(workerId, batchSize, lease, eventTypes);
        int published = 0;

        for (IntegrationOutboxService.Event event : claimed) {
            try {
                for (IntegrationEventHandler handler : handlersByType.get(event.type())) {
                    handler.handle(event);
                }
                outbox.published(event.id(), workerId);
                published++;
            } catch (Exception failure) {
                outbox.failed(event.id(), workerId, failure);
            }
        }
        return published;
    }
}
