package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Set;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformSupportServiceTest {
    @Mock PlatformSupportRepository repository;@Mock TenantLifecycleService tenants;@Mock PlatformAuditService audit;
    @Test void grantRequiresTenantPurposeTicketAndExpiryWithinFourHours(){
        Instant now=Instant.parse("2026-11-16T00:00:00Z");
        when(tenants.get(10L)).thenReturn(new TenantLifecycleService.TenantSummary(10,"solar","Solar","Solar Ltd",null,null,null,null,"ACTIVE",null,"admin@solar.test",now,now,0));
        when(repository.saveAndFlush(any(PlatformSupportGrant.class))).thenAnswer(inv->{var row=(PlatformSupportGrant)inv.getArgument(0);ReflectionTestUtils.setField(row,"id",1L);return row;});
        var service=new PlatformSupportServiceImpl(repository,tenants,audit,Clock.fixed(now,ZoneOffset.UTC));
        var auth=mock(AuthorizationContext.class);when(auth.current()).thenReturn(new AuthorizationContext.Context("platform@solar.test",null,null,Set.of("PLATFORM_SUPPORT_ACCESS"),true,false,false));
        ReflectionTestUtils.setField(service,"authorization",auth);
        var result=service.grant(10,"platform@solar.test","Investigate reported login issue","SUP-42",now.plus(Duration.ofHours(2)),"corr-42");
        assertThat(result.tenantId()).isEqualTo(10);assertThat(result.actor()).isEqualTo("platform@solar.test");assertThat(result.active()).isTrue();
        verify(audit).record(eq("platform@solar.test"),eq("SUPPORT_ACCESS_GRANTED"),eq("SUPPORT_ACCESS"),anyString(),eq(10L),contains("Investigate"),eq("corr-42"));
        assertThatThrownBy(()->service.grant(10,"platform@solar.test","Purpose","SUP-43",now.plus(Duration.ofHours(5)),null)).isInstanceOf(ResponseStatusException.class);
        verify(repository,times(1)).saveAndFlush(any());
    }
}
