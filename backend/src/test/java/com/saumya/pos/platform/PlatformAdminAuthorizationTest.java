package com.saumya.pos.platform;

import com.saumya.pos.marketplace.ProductAdminController;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.security.AuthorizationContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAdminAuthorizationTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private AuthorizationContext context(String... permissions){
        var membership=mock(MembershipService.class);when(membership.access(anyString())).thenReturn(Optional.empty());
        var auth=UsernamePasswordAuthenticationToken.authenticated("admin@platform.test",null,
            java.util.stream.Stream.concat(java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new),java.util.stream.Stream.of(new SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_PLATFORM))).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return new AuthorizationContext(membership);
    }
    @Test void platformPermissionDoesNotImplyOtherPlatformPermissions(){
        var controller=new PlatformAdminController(null,null,null,null,context("PLATFORM_SUPPORT_ACCESS"));
        assertThatThrownBy(controller::dashboard).isInstanceOf(ResponseStatusException.class).hasMessageContaining("PLATFORM_ANALYTICS_VIEW");
    }
    @Test void platformIdentityCannotUseVendorCatalogueControllerEvenWithCreateGrant(){
        var controller=new ProductAdminController(null,context("PLATFORM_TENANT_CREATE"));
        assertThatThrownBy(()->controller.list(0,20,"panel")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Active vendor membership required");
    }
}
