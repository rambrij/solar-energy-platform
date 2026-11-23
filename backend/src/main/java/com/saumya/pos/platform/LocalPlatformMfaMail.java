package com.saumya.pos.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.identity.PlatformMfaNotifications;
import com.saumya.pos.platform.internal.PlatformConfigurationEntry;
import com.saumya.pos.platform.internal.PlatformConfigurationRepository;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("local & !prod & !production")
class LocalPlatformMfaMail implements PlatformMfaNotifications {
    static final String ADAPTER_KEY = "INTEGRATION.PLATFORM_MFA_MAIL.ADAPTER";
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(LocalPlatformMfaMail.class);

    private record Template(String subject, String body) {}

    private final PlatformConfigurationRepository settings;
    private final JavaMailSender mail;
    private final ObjectMapper json;
    private final TransactionTemplate read;

    LocalPlatformMfaMail(PlatformConfigurationRepository settings, JavaMailSender mail, ObjectMapper json,
                         PlatformTransactionManager transactions) {
        this.settings = settings;
        this.mail = mail;
        this.json = json;
        this.read = new TransactionTemplate(transactions);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
    }

    @Override
    public void afterCommit(String recipient, Activity activity) {
        if (recipient == null || !recipient.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
            || !TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(recipient, activity);
            }
        });
    }

    private void send(String recipient, Activity activity) {
        try {
            Template template = read.execute(status -> {
                if (!"SMTP".equals(setting(ADAPTER_KEY, "INTEGRATION", "SMTP"))) return null;
                return template(activity);
            });
            if (template == null) return;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("platform-security@somya.local");
            message.setTo(recipient);
            message.setSubject(template.subject());
            message.setText(template.body());
            mail.send(message);
        } catch (RuntimeException e) {
            // A local notification failure cannot reverse a committed MFA change.
            LOG.warn("Platform MFA activity notice could not be sent ({})", activity);
        }
    }

    private Template template(Activity activity) {
        Template fallback = switch (activity) {
            case ENROLLMENT_ISSUED -> new Template("Platform MFA enrollment issued",
                "A platform administrator issued or reset your MFA enrollment. Contact platform support if this was unexpected.");
            case ENROLLMENT_COMPLETED -> new Template("Platform MFA enrollment completed",
                "MFA has been enrolled for your platform account. Contact platform support if this was unexpected.");
        };
        String value = setting("EMAIL_TEMPLATE.PLATFORM_MFA_" + activity.name(), "EMAIL_TEMPLATE", null);
        if (value == null) return fallback;
        try {
            var parsed = json.readTree(value);
            String subject = parsed.path("subject").asText("");
            String body = parsed.path("body").asText("");
            if (subject.isBlank() || subject.contains("\r") || subject.contains("\n") || body.isBlank()) return fallback;
            return new Template(subject, body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return fallback;
        }
    }

    private String setting(String key, String category, String fallback) {
        return settings.findById(key)
            .filter(entry -> category.equals(entry.category()) && !entry.sensitive())
            .map(PlatformConfigurationEntry::plainValue)
            .orElse(fallback);
    }
}
