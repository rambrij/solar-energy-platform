package com.saumya.pos.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.platform.internal.PlatformConfigurationEntry;
import com.saumya.pos.platform.internal.PlatformConfigurationRepository;
import com.saumya.pos.subscription.GraceReminderTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class PlatformGraceReminderTemplate implements GraceReminderTemplate {
    static final String KEY = "EMAIL_TEMPLATE.MOCK_SUBSCRIPTION_GRACE";
    static final Wording DEFAULT = new Wording("Test subscription payment failed",
        "A simulated subscription payment could not be completed.");

    private final PlatformConfigurationRepository settings;
    private final ObjectMapper json;

    PlatformGraceReminderTemplate(PlatformConfigurationRepository settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Wording load() {
        return settings.findById(KEY)
            .filter(entry -> "EMAIL_TEMPLATE".equals(entry.category()) && !entry.sensitive())
            .map(PlatformConfigurationEntry::plainValue)
            .map(this::parse)
            .orElse(DEFAULT);
    }

    private Wording parse(String value) {
        try {
            var parsed = json.readTree(value);
            String subject = parsed.path("subject").asText("");
            String body = parsed.path("body").asText("");
            if (!subject.isBlank() && !subject.contains("\r") && !subject.contains("\n") && !body.isBlank()) {
                return new Wording(subject, body);
            }
        } catch (JsonProcessingException ignored) {
            return DEFAULT;
        }
        return DEFAULT;
    }
}
