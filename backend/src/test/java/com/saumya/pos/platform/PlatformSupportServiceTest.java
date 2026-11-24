package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.util.List;
import java.time.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformSupportServiceTest {
    @Mock PlatformSupportRepository repository;@Mock TenantLifecycleService tenants;@Mock PlatformAuditService audit;
    @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}
    @Test void grantRequiresTenantPurposeTicketAndExpiryWithinFourHours(){
        Instant now=Instant.parse("2026-11-16T00:00:00Z");
        when(tenants.get(10L)).thenReturn(new TenantLifecycleService.TenantSummary(10,"solar","Solar","Solar Ltd",null,null,null,null,"ACTIVE",null,"admin@solar.test",now,now,0,null));
        when(repository.saveAndFlush(any(PlatformSupportGrant.class))).thenAnswer(inv->{var row=(PlatformSupportGrant)inv.getArgument(0);ReflectionTestUtils.setField(row,"id",1L);return row;});
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("platform@solar.test","n/a",
            List.of(new SimpleGrantedAuthority("PLATFORM_SUPPORT_ACCESS"),new SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_PLATFORM))));
        var service=new PlatformSupportServiceImpl(repository,tenants,audit,Clock.fixed(now,ZoneOffset.UTC));
        var result=service.grant(10,"platform@solar.test","Investigate reported login issue","SUP-42",now.plus(Duration.ofHours(2)),"corr-42");
        assertThat(result.tenantId()).isEqualTo(10);assertThat(result.actor()).isEqualTo("platform@solar.test");assertThat(result.active()).isTrue();
        verify(audit).record(eq("platform@solar.test"),eq("SUPPORT_ACCESS_GRANTED"),eq("SUPPORT_ACCESS"),anyString(),eq(10L),contains("Investigate"),eq("corr-42"));
        assertThatThrownBy(()->service.grant(10,"platform@solar.test","Purpose","SUP-43",now.plus(Duration.ofHours(5)),null)).isInstanceOf(ResponseStatusException.class);
        verify(repository,times(1)).saveAndFlush(any());
    }

    @Test void supportGrantRevokeAndReadRequirePlatformAuthorityBeforePersistenceOrTenantLookup(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor","n/a",
            List.of(new SimpleGrantedAuthority("VENDOR_AUDIT_VIEW"),new SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR))));
        var service=new PlatformSupportServiceImpl(repository,tenants,audit,Clock.systemUTC());

        assertThatThrownBy(()->service.grant(10,"vendor","Investigation","SUP-99",
            Instant.now().plus(Duration.ofHours(1)),"trace")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.revoke(1,"vendor","Completed","trace"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(service::recent).isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(repository,tenants,audit);
    }

}
