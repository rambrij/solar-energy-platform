package com.saumya.pos.subscription;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MockSubscriptionBillingReminderTest {
    @Test
    void usesSavedWordingOnlyAfterCommitAndPreservesBillingContext() {
        var mail = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        var template = mock(GraceReminderTemplate.class);
        when(template.load()).thenReturn(new GraceReminderTemplate.Wording("Custom reminder", "Check your billing."));
        var reminder = new MockSubscriptionBillingReminder(provider, environment, template);
        var deadline = Instant.parse("2026-11-30T12:00:00Z");

        TransactionSynchronizationManager.initSynchronization();
        try {
            reminder.afterCommit("vendor@example.test", "MOCK-SUB-12", deadline);
            verifyNoInteractions(mail, template);
            TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        var sent = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("vendor@example.test");
        assertThat(sent.getValue().getSubject()).isEqualTo("Custom reminder");
        assertThat(sent.getValue().getText()).contains("Check your billing.", "MOCK-SUB-12",
            deadline.toString(), "no funds were collected");
    }
}
