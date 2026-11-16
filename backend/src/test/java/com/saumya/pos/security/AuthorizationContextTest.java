package com.saumya.pos.security;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationContextTest {
    @Mock VendorMembershipResolver membershipService;
    private AuthorizationContext authContext;

    @BeforeEach void setUp() { authContext = new AuthorizationContext(membershipService); }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private void authenticate(String username, String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(username, "test", granted));
        SecurityContextHolder.setContext(context);
    }

    @Test void currentReturnsContextFromSecurityContext() {
        authenticate("cashier@vendor.com", "POS_VIEW", "POS_SALE_CREATE");
        when(membershipService.resolve("cashier@vendor.com")).thenReturn(Optional.of(new VendorMembershipResolver.Membership(1L,10L,"cashier@vendor.com",List.of("POS_VIEW","POS_SALE_CREATE"))));
        var ctx=authContext.current();
        assertThat(ctx.username()).isEqualTo("cashier@vendor.com");assertThat(ctx.tenantId()).isEqualTo(1L);assertThat(ctx.branchId()).isEqualTo(10L);
        assertThat(ctx.permissions()).contains("POS_VIEW","POS_SALE_CREATE");assertThat(ctx.isVendorEmployee()).isTrue();assertThat(ctx.isCustomer()).isFalse();assertThat(ctx.isPlatformUser()).isFalse();
    }
    @Test void requirePermissionThrowsWhenMissing() {
        authenticate("cashier@vendor.com","POS_VIEW");
        when(membershipService.access("cashier@vendor.com")).thenReturn(Optional.of(new MembershipService.Access(1L,10L,"cashier@vendor.com",List.of("POS_VIEW"))));
        var ctx=authContext.current();
        assertThatThrownBy(()->ctx.requirePermission("POS_REFUND_APPROVE")).isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN).hasMessageContaining("Missing required permission");
    }
    @Test void requirePermissionSucceedsWhenPresent() {
        authenticate("cashier@vendor.com","POS_VIEW");
        when(membershipService.access("cashier@vendor.com")).thenReturn(Optional.of(new MembershipService.Access(1L,10L,"cashier@vendor.com",List.of("POS_VIEW"))));
        authContext.current().requirePermission("POS_VIEW");
    }
    @Test void requireTenantBlocksCrossTenantAccess() {
        authenticate("cashierA@vendor.com","POS_VIEW");
        when(membershipService.access("cashierA@vendor.com")).thenReturn(Optional.of(new MembershipService.Access(1L,10L,"cashierA@vendor.com",List.of("POS_VIEW"))));
        assertThatThrownBy(()->authContext.current().requireTenant(2L)).isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN).hasMessageContaining("Cross-tenant access denied");
    }
    @Test void requireTenantSucceedsForOwnTenant() {
        authenticate("cashierA@vendor.com","POS_VIEW");
        when(membershipService.access("cashierA@vendor.com")).thenReturn(Optional.of(new MembershipService.Access(1L,10L,"cashierA@vendor.com",List.of("POS_VIEW"))));
        authContext.current().requireTenant(1L);
    }
    @Test void requireBranchBlocksWrongBranchAccess() {
        authenticate("cashierA@vendor.com","POS_VIEW");
        when(membershipService.access("cashierA@vendor.com")).thenReturn(Optional.of(new MembershipService.Access(1L,10L,"cashierA@vendor.com",List.of("POS_VIEW"))));
        assertThatThrownBy(()->authContext.current().requireBranch(20L)).isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN).hasMessageContaining("Branch assignment mismatch");
    }
    @Test void requireBranchSucceedsForAssignedBranch() {
        authenticate("cashierA@vendor.com","POS_VIEW");
        when(membershipService.access("cashierA@vendor.com")).thenReturn(Optional.of(new MembershipService.Access(1L,10L,"cashierA@vendor.com",List.of("POS_VIEW"))));
        authContext.current().requireBranch(10L);
    }
    @Test void customerHasNoVendorMembership() {
        authenticate("customer@example.com","CUSTOMER");when(membershipService.access("customer@example.com")).thenReturn(Optional.empty());
        var ctx=authContext.current();assertThat(ctx.isCustomer()).isTrue();assertThat(ctx.isVendorEmployee()).isFalse();assertThat(ctx.isPlatformUser()).isFalse();assertThat(ctx.tenantId()).isNull();assertThat(ctx.branchId()).isNull();
    }
    @Test void requireVendorMembershipThrowsForCustomer() {
        authenticate("customer@example.com","CUSTOMER");when(membershipService.access("customer@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(()->authContext.current().requireVendorMembership()).isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN).hasMessageContaining("vendor membership required");
    }
    @Test void platformUserHasNoVendorOrCustomerPermissions() {
        authenticate("admin@platform.com","PLATFORM_SUPER_ADMIN");
        var ctx=authContext.current();assertThat(ctx.isPlatformUser()).isTrue();assertThat(ctx.isVendorEmployee()).isFalse();assertThat(ctx.isCustomer()).isFalse();assertThat(ctx.tenantId()).isNull();assertThat(ctx.branchId()).isNull();assertThat(ctx.permissions()).containsExactly("PLATFORM_SUPER_ADMIN");
    }
}
