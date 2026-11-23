package com.saumya.pos.platform;

import com.saumya.pos.identity.IdentityProvisioningService;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.retail.BranchProvisioningService;
import com.saumya.pos.subscription.TenantSubscriptionService;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PlatformAdminServiceAuthorizationTest {
    @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}
    private void authenticate(String username,String... permissions){
        var authorities=java.util.stream.Stream.concat(java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new),java.util.stream.Stream.of(new SimpleGrantedAuthority(permissions.length>0&&permissions[0].startsWith("PLATFORM_")?com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_PLATFORM:com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR))).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username,"n/a",authorities));
    }
    private PlatformAdminService service(TenantLifecycleService tenants){
        return new PlatformAdminService(tenants,mock(BranchProvisioningService.class),mock(MembershipService.class),
            mock(IdentityProvisioningService.class),mock(PlatformAuditService.class),mock(HealthEndpoint.class),Clock.systemUTC(),
            mock(TenantSubscriptionService.class));
    }
    @Test void onboardingIsDeniedAtApplicationServiceWithoutCreatePermission(){
        var tenants=mock(TenantLifecycleService.class);authenticate("operator@platform.test","PLATFORM_SUPPORT_ACCESS");
        assertThatThrownBy(()->service(tenants).onboard(null,"operator@platform.test",null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PLATFORM_TENANT_CREATE");
        verifyNoInteractions(tenants);
    }
    @Test void actorCannotInvokeTenantUpdateForAnotherPlatformIdentity(){
        var tenants=mock(TenantLifecycleService.class);authenticate("actual@platform.test","PLATFORM_TENANT_UPDATE");
        assertThatThrownBy(()->service(tenants).update(1,null,"forged@platform.test",null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("does not match");
        verifyNoInteractions(tenants);
    }
    @Test void dashboardRequiresAnalyticsPermissionAtApplicationService(){
        var tenants=mock(TenantLifecycleService.class);authenticate("operator@platform.test","PLATFORM_AUDIT_VIEW");
        assertThatThrownBy(()->service(tenants).dashboard()).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PLATFORM_ANALYTICS_VIEW");
        verifyNoInteractions(tenants);
    }
    @Test void vendorAuthorityCannotInvokePlatformOperations(){
        var tenants=mock(TenantLifecycleService.class);authenticate("vendor","VENDOR_AUDIT_VIEW");
        assertThatThrownBy(()->service(tenants).tenants(null,null,0,20)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(tenants);
    }

    @Test void tenantTransitionRequiresTheSpecificLifecyclePermissionBeforeTenantReadsOrWrites(){
        var tenants=mock(TenantLifecycleService.class);
        authenticate("operator@platform.test","PLATFORM_TENANT_UPDATE");
        assertThatThrownBy(()->service(tenants).transition(910001L,
            com.saumya.pos.tenant.TenantStatus.SUSPENDED,"P0-02 denial","operator@platform.test",null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PLATFORM_TENANT_SUSPEND");
        verifyNoInteractions(tenants);
    }
}
