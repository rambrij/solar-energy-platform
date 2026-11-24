package com.saumya.pos.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.platform.internal.PlatformConfigurationEntry;
import com.saumya.pos.platform.internal.PlatformConfigurationRepository;
import com.saumya.pos.subscription.GraceReminderTemplate.Wording;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlatformGraceReminderTemplateTest {
    @Test
    void readsCurrentWordingAndFallsBackForMissingOrMalformedSettings() {
        var repository = mock(PlatformConfigurationRepository.class);
        var template = new PlatformGraceReminderTemplate(repository, new ObjectMapper());
        String key = PlatformGraceReminderTemplate.KEY;
        when(repository.findById(key)).thenReturn(Optional.empty());
        assertThat(template.load()).isEqualTo(PlatformGraceReminderTemplate.DEFAULT);

        var entry = new PlatformConfigurationEntry(key, "EMAIL_TEMPLATE",
            "{\"subject\":\"Grace reminder\",\"body\":\"Check billing.\"}", null, false,
            "platform@example.test", Instant.EPOCH);
        when(repository.findById(key)).thenReturn(Optional.of(entry));
        assertThat(template.load()).isEqualTo(new Wording("Grace reminder", "Check billing."));

        entry.update("EMAIL_TEMPLATE", "{bad json", null, false, "platform@example.test", Instant.EPOCH);
        assertThat(template.load()).isEqualTo(PlatformGraceReminderTemplate.DEFAULT);
    }
}
