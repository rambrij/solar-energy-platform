package com.saumya.pos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.integration.IntegrationEventHandler;
import com.saumya.pos.integration.IntegrationOutboxService;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Delivers SLA escalation outbox events to verified, active employees in the
 * event's tenant. The outbox is at-least-once: SMTP may accept a message just
 * before a process crash, so a retried event can produce a duplicate email.
 */
@Component
public class ServiceSlaEscalationEmailHandler implements IntegrationEventHandler {
    private static final String EVENT_TYPE = "SERVICE_SLA.ESCALATED";
    private final ServiceSlaEscalationService escalations;
    private final JavaMailSender mail;
    private final ObjectMapper mapper;
    private final String from;
    private final String publicUrl;

    public ServiceSlaEscalationEmailHandler(ServiceSlaEscalationService escalations,
            JavaMailSender mail, ObjectMapper mapper,
            @Value("${app.mail.from:noreply@somya.local}") String from,
            @Value("${app.public-url:http://localhost:8080}") String publicUrl) {
        this.escalations = escalations;
        this.mail = mail;
        this.mapper = mapper;
        if (!StringUtils.hasText(from) || from.length() > 100
                || from.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("A valid escalation notification sender is required");
        }
        this.from = from.strip();
        this.publicUrl = normalizePublicUrl(publicUrl);
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(EVENT_TYPE);
    }

    @Override
    public void handle(IntegrationOutboxService.Event event) {
        if (event == null || event.tenant() == null || event.tenant() <= 0
                || !EVENT_TYPE.equals(event.type())) {
            throw new IllegalArgumentException("A tenant-owned SLA escalation event is required");
        }
        JsonNode payload;
        try {
            payload = mapper.readTree(event.payload());
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid SLA escalation event payload", failure);
        }
        long escalationId = positiveLong(payload, "escalationId");
        String recipientRole = required(payload, "recipientRoleKey", 64);
        String caseType = enumValue(payload, "caseType", Set.of("SERVICE_REQUEST", "WARRANTY_CLAIM"));
        String breachType = enumValue(payload, "breachType", Set.of("RESPONSE", "RESOLUTION"));
        long caseId = positiveLong(payload, "caseId");
        int level = payload.path("level").asInt(-1);
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Invalid SLA escalation level");
        }
        Instant escalatedAt;
        try {
            escalatedAt = Instant.parse(required(payload, "escalatedAt", 40));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid SLA escalation timestamp", failure);
        }

        var recipients = escalations.resolveEscalationRecipients(event.tenant(), recipientRole);
        if (recipients.isEmpty()) {
            throw new IllegalStateException("No active, verified tenant recipients for SLA escalation role "
                + recipientRole);
        }

        String link = publicUrl + "/vendor/customer-management/engineer-visits";
        for (String recipient : recipients) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient);
            message.setSubject("Action required: service SLA escalation level " + level);
            message.setText("""
                A service case has reached SLA escalation level %d.

                Case type: %s
                Case reference: %d
                Breach type: %s
                Escalation reference: %d
                Escalated at: %s

                Review the case in your vendor workspace:
                %s

                This notification was sent to active, verified members of the configured tenant role.
                """.formatted(level, caseType, caseId, breachType, escalationId, escalatedAt, link));
            mail.send(message);
        }
    }

    private static long positiveLong(JsonNode payload, String field) {
        long value = payload.path(field).asLong(-1);
        if (value <= 0 || !payload.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("Invalid SLA escalation field: " + field);
        }
        return value;
    }

    private static String required(JsonNode payload, String field, int maxLength) {
        String value = payload.path(field).asText("");
        if (!StringUtils.hasText(value) || value.length() > maxLength
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid SLA escalation field: " + field);
        }
        return value.strip();
    }

    private static String enumValue(JsonNode payload, String field, Set<String> allowed) {
        String value = required(payload, field, 32);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("Unsupported SLA escalation field: " + field);
        }
        return value;
    }

    private static String normalizePublicUrl(String value) {
        String base = value == null ? "" : value.strip().replaceAll("/+$", "");
        try {
            var uri = java.net.URI.create(base);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Public application URL must be an HTTP(S) base URL");
            }
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Valid public application URL required", failure);
        }
        return base;
    }
}
