package com.saumya.pos.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.integration.internal.OutboxMessage;
import com.saumya.pos.integration.internal.OutboxMessageRepository;
import com.saumya.pos.shared.EventOutbox;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IntegrationOutboxService implements EventOutbox {
    public record Event(
        String id,
        Long tenant,
        String actor,
        String aggregate,
        String aggregateId,
        String type,
        int version,
        String correlation,
        String payload,
        Instant createdAt
    ) {}

    private final OutboxMessageRepository rows;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int maxAttempts;

    public IntegrationOutboxService(
            OutboxMessageRepository rows,
            ObjectMapper mapper,
            Clock clock,
            @Value("${integration.outbox.max-attempts:10}") int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 25) {
            throw new IllegalArgumentException("Outbox max attempts must be 1..25");
        }
        this.rows = rows;
        this.mapper = mapper;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional
    public String append(
            Long tenant,
            String actor,
            String aggregate,
            String aggregateId,
            String type,
            String correlation,
            Map<String, Object> payload) {
        if (tenant != null && tenant <= 0) {
            throw new IllegalArgumentException("Invalid tenant");
        }
        require(actor, 100);
        require(aggregate, 80);
        require(aggregateId, 100);
        require(type, 120);
        if (payload == null) {
            throw new IllegalArgumentException("Event payload is required");
        }

        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid event payload", failure);
        }
        if (json.length() > 65_536) {
            throw new IllegalArgumentException("Event payload exceeds 64 KB");
        }

        String eventId = UUID.randomUUID().toString();
        String correlationId = StringUtils.hasText(correlation)
            ? correlation
            : UUID.randomUUID().toString();
        rows.save(new OutboxMessage(
            eventId, tenant, actor, aggregate, aggregateId, type, correlationId, json, clock.instant()));
        return eventId;
    }

    @Transactional
    public List<Event> claim(String worker, int limit, Duration lease) {
        return claimRows(rows.claimable(clock.instant(), PageRequest.of(0, boundedLimit(limit))),
            worker, lease);
    }

    /*
     * Only the dispatcher in this module can use this filtered claim path.
     * Events without a registered handler remain PENDING and are not consumed.
     */
    @Transactional
    List<Event> claimForTypes(String worker, int limit, Duration lease, Set<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of();
        }
        return claimRows(rows.claimableForTypes(
            clock.instant(), eventTypes, PageRequest.of(0, boundedLimit(limit))), worker, lease);
    }

    private List<Event> claimRows(List<OutboxMessage> messages, String worker, Duration lease) {
        if (!StringUtils.hasText(worker) || worker.length() > 36) {
            throw new IllegalArgumentException("Invalid outbox worker identity");
        }
        if (lease == null || lease.isZero() || lease.isNegative() || lease.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Outbox lease must be greater than zero and at most 10 minutes");
        }
        Instant leaseUntil = clock.instant().plus(lease);
        return messages.stream().map(message -> {
            message.claim(worker, leaseUntil);
            return new Event(
                message.eventId(), message.tenantId(), message.actor(), message.aggregateType(),
                message.aggregateId(), message.eventType(), message.schemaVersion(),
                message.correlationId(), message.payloadJson(), message.createdAt());
        }).toList();
    }

    @Transactional
    public void published(String id, String worker) {
        rows.findByEventIdAndLeaseOwnerAndState(id, worker, "PROCESSING")
            .ifPresent(message -> message.published(clock.instant()));
    }

    @Transactional
    public void failed(String id, String worker, Throwable cause) {
        rows.findByEventIdAndLeaseOwnerAndState(id, worker, "PROCESSING").ifPresent(message -> {
            long delay = Math.min(3_600L, 5L << Math.min(10, Math.max(0, message.attemptCount() - 1)));
            String error = cause == null ? "UnknownDeliveryFailure" : cause.getClass().getSimpleName();
            message.failed(clock.instant().plusSeconds(delay), error, maxAttempts);
        });
    }

    private static int boundedLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Outbox batch size must be 1..100");
        }
        return limit;
    }

    private static void require(String value, int max) {
        if (!StringUtils.hasText(value) || value.length() > max) {
            throw new IllegalArgumentException("Invalid event metadata");
        }
    }
}
