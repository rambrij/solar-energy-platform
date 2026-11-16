package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAuditServiceTest {
    @Mock PlatformAuditRepository repository;
    @Test void recordsActorResourceReasonTenantAndCorrelationAsAnAppendOnlyEvent(){
        Instant now=Instant.parse("2026-11-16T00:00:00Z");
        when(repository.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        var service=new PlatformAuditServiceImpl(repository,Clock.fixed(now,ZoneOffset.UTC));
        service.record("platform@example.test","TENANT_SUSPENDED","TENANT","12",12L,"Risk review","trace-12");
        var captor=ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        var event=captor.getValue();
        assertThat(event.actor()).isEqualTo("platform@example.test");assertThat(event.action()).isEqualTo("TENANT_SUSPENDED");
        assertThat(event.resourceId()).isEqualTo("12");assertThat(event.tenantId()).isEqualTo(12L);
        assertThat(event.reason()).isEqualTo("Risk review");assertThat(event.correlationId()).isEqualTo("trace-12");
        assertThat(event.outcome()).isEqualTo("SUCCESS");assertThat(event.occurredAt()).isEqualTo(now);
    }
}
