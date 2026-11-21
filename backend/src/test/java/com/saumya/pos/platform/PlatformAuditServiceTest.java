package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import java.time.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAuditServiceTest {
    @Mock PlatformAuditRepository repository;
    @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}
    @Test void recordsActorResourceReasonTenantAndCorrelationAsAnAppendOnlyEvent(){
        Instant now=Instant.parse("2026-11-16T00:00:00Z");
        when(repository.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("platform@example.test","n/a",
            java.util.List.of(new SimpleGrantedAuthority("PLATFORM_TENANT_SUSPEND"))));
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
    @Test void directAuditWriteRequiresMatchingPlatformActorAndActionPermission(){
        var service=new PlatformAuditServiceImpl(repository,Clock.systemUTC());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor","n/a",
            java.util.List.of(new SimpleGrantedAuthority("VENDOR_AUDIT_VIEW"))));
        assertThatThrownBy(()->service.record("vendor","TENANT_CREATED","TENANT","1",1L,"reason","trace"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test void subscriptionAdministrationAuditActionsRequireTheirMatchingPlatformGrants(){
        var service=new PlatformAuditServiceImpl(repository,Clock.systemUTC());
        when(repository.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("platform@example.test","n/a",
            java.util.List.of(new SimpleGrantedAuthority("PLATFORM_PLAN_MANAGE"))));
        for(String action:java.util.List.of("SUBSCRIPTION_PLAN_CREATED","SUBSCRIPTION_PLAN_VERSION_CREATED",
                "SUBSCRIPTION_PLAN_ENABLED","SUBSCRIPTION_PLAN_DISABLED"))
            service.record("platform@example.test",action,"SUBSCRIPTION_PLAN","1",null,"Plan administration","trace-plan");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("platform@example.test","n/a",
            java.util.List.of(new SimpleGrantedAuthority("PLATFORM_SUBSCRIPTION_ADJUST"))));
        for(String action:java.util.List.of("TENANT_SUBSCRIPTION_CREATED","TENANT_SUBSCRIPTION_STATUS_CHANGED"))
            service.record("platform@example.test",action,"TENANT_SUBSCRIPTION","1",910001L,"Subscription administration","trace-subscription");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("platform@example.test","n/a",
            java.util.List.of(new SimpleGrantedAuthority("PLATFORM_IDENTITY_MANAGE"))));
        service.record("platform@example.test","PLATFORM_MFA_ENROLLMENT_ISSUED","PLATFORM_IDENTITY","target@example.test",null,"MFA reset","trace-mfa");
        verify(repository,times(7)).saveAndFlush(any());
    }

    @Test void subscriptionAuditActionsRejectActorsWithoutTheirSpecificPlatformGrant(){
        var service=new PlatformAuditServiceImpl(repository,Clock.systemUTC());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("platform@example.test","n/a",
            java.util.List.of(new SimpleGrantedAuthority("PLATFORM_SUBSCRIPTION_VIEW"))));
        assertThatThrownBy(()->service.record("platform@example.test","SUBSCRIPTION_PLAN_CREATED","SUBSCRIPTION_PLAN","1",null,"reason","trace"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.record("platform@example.test","TENANT_SUBSCRIPTION_CREATED","TENANT_SUBSCRIPTION","1",910001L,"reason","trace"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test void auditSearchRequiresPlatformAuditPermission(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor","n/a",
            java.util.List.of(new SimpleGrantedAuthority("VENDOR_AUDIT_VIEW"))));
        var service=new PlatformAuditServiceImpl(repository,Clock.systemUTC());
        assertThatThrownBy(()->service.search(0,20,null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
