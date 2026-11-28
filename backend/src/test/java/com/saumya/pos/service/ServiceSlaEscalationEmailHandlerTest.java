package com.saumya.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.integration.IntegrationOutboxService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ServiceSlaEscalationEmailHandlerTest {
    private final ServiceSlaEscalationService escalations = mock(ServiceSlaEscalationService.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private ServiceSlaEscalationEmailHandler handler() {
        return new ServiceSlaEscalationEmailHandler(escalations, mail, new ObjectMapper(),
            "alerts@saumya.test", "https://marketplace.example.test");
    }
    private IntegrationOutboxService.Event event(Long tenant, String payload) {
        return new IntegrationOutboxService.Event("event-1", tenant, "service-sla-worker",
            "SERVICE_SLA_ESCALATION", "41", "SERVICE_SLA.ESCALATED", 1, "corr-1",
            payload, Instant.parse("2026-11-28T10:00:00Z"));
    }
    private String payload() {
        return """
            {"escalationId":41,"slaId":18,"caseType":"SERVICE_REQUEST","caseId":72,
             "breachType":"RESPONSE","level":2,"recipientRoleKey":"SERVICE_MANAGER",
             "escalatedAt":"2026-11-28T10:00:00Z"}
            """;
    }

    @Test void registersOnlySlaEscalationDeliveryEvent() {
        assertThat(handler().eventTypes()).containsExactly("SERVICE_SLA.ESCALATED");
    }

    @Test void sendsOnePlainTextMessageToEveryResolvedTenantRecipient() {
        when(escalations.resolveEscalationRecipients(7L, "SERVICE_MANAGER"))
            .thenReturn(List.of("manager.one@vendor.test", "manager.two@vendor.test"));

        handler().handle(event(7L, payload()));

        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(messages.capture());
        assertThat(messages.getAllValues()).extracting(SimpleMailMessage::getTo)
            .containsExactly(
                new String[]{"manager.one@vendor.test"},
                new String[]{"manager.two@vendor.test"});
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getFrom()).isEqualTo("alerts@saumya.test");
            assertThat(message.getSubject()).contains("level 2");
            assertThat(message.getText()).contains("SERVICE_REQUEST", "72", "RESPONSE", "41");
            assertThat(message.getText()).contains("https://marketplace.example.test/vendor/customer-management/engineer-visits");
        });
        verify(escalations).resolveEscalationRecipients(7L, "SERVICE_MANAGER");
    }

    @Test void missingVerifiedRoleRecipientsLeavesOutboxEventRetryable() {
        when(escalations.resolveEscalationRecipients(7L, "SERVICE_MANAGER")).thenReturn(List.of());

        assertThatThrownBy(() -> handler().handle(event(7L, payload())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No active, verified tenant recipients");
        verifyNoInteractions(mail);
    }

    @Test void rejectsGlobalEventsWithoutTenantOwnership() {
        assertThatThrownBy(() -> handler().handle(event(null, payload())))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(escalations, mail);
    }

    @Test void rejectsMalformedOrUnsupportedPayloadBeforeRecipientLookup() {
        assertThatThrownBy(() -> handler().handle(event(7L, "{not-json")))
            .isInstanceOf(IllegalArgumentException.class);
        String unsupported = payload().replace("SERVICE_MANAGER", "PLATFORM_ADMIN");
        assertThatThrownBy(() -> handler().handle(event(7L, unsupported)))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(escalations, mail);
    }

    @Test void rejectsInvalidPublicUrlAtConstruction() {
        assertThatThrownBy(() -> new ServiceSlaEscalationEmailHandler(
            escalations, mail, new ObjectMapper(), "alerts@saumya.test", "javascript:alert(1)"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
