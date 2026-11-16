package com.saumya.pos.platform;

import com.saumya.pos.identity.IdentityProvisioningService;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.retail.BranchProvisioningService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PlatformAdminServiceAuthorizationTest {
    @Test void onboardingIsDeniedAtApplicationServiceWithoutCreatePermission(){
        var tenants=mock(TenantLifecycleService.class);var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("operator@platform.test",null,null,
            Set.of("PLATFORM_SUPPORT_ACCESS"),true,false,false));
        var service=new PlatformAdminService(tenants,mock(BranchProvisioningService.class),mock(MembershipService.class),
            mock(IdentityProvisioningService.class),mock(PlatformAuditService.class),mock(HealthEndpoint.class),Clock.systemUTC(),auth);
        assertThatThrownBy(()->service.onboard(null,"operator@platform.test",null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PLATFORM_TENANT_CREATE");
        verifyNoInteractions(tenants);
    }
    @Test void actorCannotInvokeTenantUpdateForAnotherPlatformIdentity(){
        var tenants=mock(TenantLifecycleService.class);var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("actual@platform.test",null,null,
            Set.of("PLATFORM_TENANT_UPDATE"),true,false,false));
        var service=new PlatformAdminService(tenants,mock(BranchProvisioningService.class),mock(MembershipService.class),
            mock(IdentityProvisioningService.class),mock(PlatformAuditService.class),mock(HealthEndpoint.class),Clock.systemUTC(),auth);
        assertThatThrownBy(()->service.update(1,null,"forged@platform.test",null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("does not match");
        verifyNoInteractions(tenants);
    }
    @Test void dashboardRequiresAnalyticsPermissionAtApplicationService(){
        var tenants=mock(TenantLifecycleService.class);var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("operator@platform.test",null,null,
            Set.of("PLATFORM_AUDIT_VIEW"),true,false,false));
        var service=new PlatformAdminService(tenants,mock(BranchProvisioningService.class),mock(MembershipService.class),
            mock(IdentityProvisioningService.class),mock(PlatformAuditService.class),mock(HealthEndpoint.class),Clock.systemUTC(),auth);
        assertThatThrownBy(service::dashboard).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PLATFORM_ANALYTICS_VIEW");
        verifyNoInteractions(tenants);
    }
}
