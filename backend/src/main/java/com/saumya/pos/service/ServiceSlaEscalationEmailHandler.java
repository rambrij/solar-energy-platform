package com.saumya.pos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.integration.IntegrationEventHandler;
import com.saumya.pos.integration.IntegrationOutboxService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
                || !from.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
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
                || !EVENT_TYPE.equals(event.type()) || !isEventId(event.id())) {
            throw new IllegalArgumentException("A tenant-owned SLA escalation event is required");
        }
        JsonNode payload;
        try {
            payload = mapper.readTree(event.payload());
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid SLA escalation event payload", failure);
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("SLA escalation payload must be a JSON object");
        }
        long escalationId = positiveLong(payload, "escalationId");
        String recipientRole = required(payload, "recipientRoleKey", 64);
        String caseType = enumValue(payload, "caseType", Set.of("SERVICE_REQUEST", "WARRANTY_CLAIM"));
        String breachType = enumValue(payload, "breachType", Set.of("RESPONSE", "RESOLUTION"));
        long caseId = positiveLong(payload, "caseId");
        JsonNode levelNode = payload.path("level");
        int level = levelNode.asInt(-1);
        if (!levelNode.isIntegralNumber() || level < 1 || level > 5) {
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

        String link = publicUrl + "/vendor/service-visits";
        String messageId = "<" + event.id() + "@" + senderDomain() + ">";
        String body = """
            A service case has reached SLA escalation level %d.

            Case type: %s
            Case reference: %d
            Breach type: %s
            Escalation reference: %d
            Escalated at: %s

            Review the case in your vendor workspace:
            %s

            This notification was sent to active, verified members of the configured tenant role.
            """.formatted(level, caseType, caseId, breachType, escalationId, escalatedAt, link);
        for (String recipient : recipients) {
            try {
                MimeMessage message = mail.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo(recipient);
                helper.setSubject("Action required: service SLA escalation level " + level);
                helper.setText(body, false);
                message.setHeader("Message-ID", messageId);
                message.setHeader("X-Idempotency-Key", event.id());
                mail.send(message);
            } catch (Exception failure) {
                throw new IllegalStateException("SLA escalation email delivery failed", failure);
            }
        }
    }

    private String senderDomain() {
        return from.substring(from.lastIndexOf('@') + 1);
    }

    private static boolean isEventId(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static long positiveLong(JsonNode payload, String field) {
        JsonNode node = payload.path(field);
        long value = node.asLong(-1);
        if (!node.isIntegralNumber() || value <= 0 || !node.canConvertToLong()) {
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
