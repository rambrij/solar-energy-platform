package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformAuditServiceTest {
    @Mock PlatformAuditRepository repository;
    @Test void recordsActorResourceReasonTenantAndCorrelationAsAnAppendOnlyEvent(){
        Instant now=Instant.parse("2026-11-16T00:00:00Z");
        when(repository.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        var auth=mock(AuthorizationContext.class);
        var service=new PlatformAuditServiceImpl(repository,Clock.fixed(now,ZoneOffset.UTC),auth);
        service.record("platform@example.test","TENANT_SUSPENDED","TENANT","12",12L,"Risk review","trace-12");
        var captor=ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        var event=captor.getValue();
        assertThat(event.actor()).isEqualTo("platform@example.test");assertThat(event.action()).isEqualTo("TENANT_SUSPENDED");
        assertThat(event.resourceId()).isEqualTo("12");assertThat(event.tenantId()).isEqualTo(12L);
        assertThat(event.reason()).isEqualTo("Risk review");assertThat(event.correlationId()).isEqualTo("trace-12");
        assertThat(event.outcome()).isEqualTo("SUCCESS");assertThat(event.occurredAt()).isEqualTo(now);
    }
    @Test void auditSearchRequiresPlatformAuditPermission(){
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("vendor",1L,1L,Set.of("VENDOR_AUDIT_VIEW"),false,false,true));
        var service=new PlatformAuditServiceImpl(repository,Clock.systemUTC(),auth);
        assertThatThrownBy(()->service.search(0,20,null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
