package com.saumya.pos.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.identity.PlatformMfaNotifications.Activity;
import com.saumya.pos.platform.internal.PlatformConfigurationEntry;
import com.saumya.pos.platform.internal.PlatformConfigurationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalPlatformMfaMailTest {
    private final PlatformConfigurationRepository settings = mock(PlatformConfigurationRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final LocalPlatformMfaMail service = new LocalPlatformMfaMail(settings, mail, new ObjectMapper(), transactions);

    @AfterEach void clearSynchronization() {
        TransactionSynchronizationManager.clear();
    }

    @Test void sendsSavedTemplateOnlyAfterCommit() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        String key = "EMAIL_TEMPLATE.PLATFORM_MFA_ENROLLMENT_ISSUED";
        when(settings.findById(key)).thenReturn(Optional.of(entry(key, "EMAIL_TEMPLATE",
            "{\"subject\":\"MFA activity\",\"body\":\"Your account MFA was reset.\"}")));

        TransactionSynchronization callback = schedule(Activity.ENROLLMENT_ISSUED);
        verifyNoInteractions(mail, settings);
        callback.afterCommit();

        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("operator@somya.local");
        assertThat(sent.getValue().getSubject()).isEqualTo("MFA activity");
        assertThat(sent.getValue().getText()).isEqualTo("Your account MFA was reset.");
    }

    @Test void disabledAdapterSkipsDeliveryAndRollbackNeverSends() {
        TransactionSynchronization rolledBack = schedule(Activity.ENROLLMENT_COMPLETED);
        rolledBack.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(mail, settings);
        TransactionSynchronizationManager.clearSynchronization();

        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(settings.findById(LocalPlatformMfaMail.ADAPTER_KEY)).thenReturn(Optional.of(entry(
            LocalPlatformMfaMail.ADAPTER_KEY, "INTEGRATION", "DISABLED")));
        schedule(Activity.ENROLLMENT_COMPLETED).afterCommit();
        verifyNoInteractions(mail);
    }

    private TransactionSynchronization schedule(Activity activity) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        service.afterCommit("operator@somya.local", activity);
        return TransactionSynchronizationManager.getSynchronizations().getFirst();
    }

    private static PlatformConfigurationEntry entry(String key, String category, String value) {
        return new PlatformConfigurationEntry(key, category, value, null, false, "admin", Instant.EPOCH);
    }
}
