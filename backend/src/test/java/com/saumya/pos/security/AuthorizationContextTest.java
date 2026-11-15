package com.saumya.pos.security;

import com.saumya.pos.membership.MembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class AuthorizationContextTest {
    
    @Autowired
    private AuthorizationContext authContext;
    
    @MockBean
    private MembershipService membershipService;
    
    @Test
    @WithMockUser(username = "cashier@vendor.com", authorities = {"POS_VIEW", "POS_SALE_CREATE"})
    void currentReturnsContextFromSecurityContext() {
        // Setup membership
        when(membershipService.access("cashier@vendor.com"))
            .thenReturn(Optional.of(new MembershipService.Access(
                1L, 10L, "cashier@vendor.com", 
                List.of("POS_VIEW", "POS_SALE_CREATE")
            )));
        
        var ctx = authContext.current();
        
        assertThat(ctx.username()).isEqualTo("cashier@vendor.com");
        assertThat(ctx.tenantId()).isEqualTo(1L);
        assertThat(ctx.branchId()).isEqualTo(10L);
        assertThat(ctx.permissions()).contains("POS_VIEW", "POS_SALE_CREATE");
        assertThat(ctx.isVendorEmployee()).isTrue();
        assertThat(ctx.isCustomer()).isFalse();
        assertThat(ctx.isPlatformUser()).isFalse();
    }
    
    @Test
    @WithMockUser(username = "cashier@vendor.com", authorities = {"POS_VIEW"})
    void requirePermissionThrowsWhenMissing() {
        when(membershipService.access("cashier@vendor.com"))
            .thenReturn(Optional.of(new MembershipService.Access(
                1L, 10L, "cashier@vendor.com", 
                List.of("POS_VIEW")
            )));
        
        var ctx = authContext.current();
        
        assertThatThrownBy(() -> ctx.requirePermission("POS_REFUND_APPROVE"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
            .hasMessageContaining("Missing required permission");
    }
    
    @Test
    @WithMockUser(username = "cashier@vendor.com", authorities = {"POS_VIEW"})
    void requirePermissionSucceedsWhenPresent() {
        when(membershipService.access("cashier@vendor.com"))
            .thenReturn(Optional.of(new MembershipService.Access(
                1L, 10L, "cashier@vendor.com", 
                List.of("POS_VIEW")
            )));
        
        var ctx = authContext.current();
        
        // Should not throw
        ctx.requirePermission("POS_VIEW");
    }
    
    @Test
    @WithMockUser(username = "cashierA@vendor.com", authorities = {"POS_VIEW"})
    void requireTenantBlocksCrossTenantAccess() {
        // User belongs to tenant 1
        when(membershipService.access("cashierA@vendor.com"))
            .thenReturn(Optional.of(new MembershipService.Access(
                1L, 10L, "cashierA@vendor.com", 
                List.of("POS_VIEW")
            )));
        
        var ctx = authContext.current();
        
        // Attempt to access tenant 2 data
        assertThatThrownBy(() -> ctx.requireTenant(2L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
            .hasMessageContaining("Cross-tenant access denied");
    }
    
    @Test
    @WithMockUser(username = "cashierA@vendor.com", authorities = {"POS_VIEW"})
    void requireTenantSucceedsForOwnTenant() {
        when(membershipService.access("cashierA@vendor.com"))
            .thenReturn(Optional.of(new MembershipService.Access(
                1L, 10L, "cashierA@vendor.com", 
                List.of("POS_VIEW")
            )));
        
        var ctx = authContext.current();
        
        // Should not throw when accessing own tenant
        ctx.requireTenant(1L);
    }
    
    @Test
    @WithMockUser(username = "cashierA@vendor.com", authorities = {"POS_VIEW"})
    void requireBranchBlocksWrongBranchAccess() {
        // Cashier assigned to branch 10
        when(membershipService.access("cashierA@vendor.com"))
            .thenReturn(Optional.of(new MembershipService.Access(
                1L, 10L, "cashierA@vendor.com", 
                List.of("POS_VIEW")
            )));
        
        var ctx = authContext.current();
        
        // Attempt to operate on branch 20
        assertThatThrownBy(() -> ctx.requireBranch(20L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
            .hasMessageContaining("Branch assignment mismatch");
    }
    
    @Test
    @WithMockUser(username = "cashierA@vendor.com", authorities = {"POS_VIEW"})
    void requireBranchSucceedsForAssignedBranch() {
        when(membershipService.access("cashierA@vendor.com"))
            .thenReturn(Optional.of(new MembershipService.Access(
                1L, 10L, "cashierA@vendor.com", 
                List.of("POS_VIEW")
            )));
        
        var ctx = authContext.current();
        
        // Should not throw for assigned branch
        ctx.requireBranch(10L);
    }
    
    @Test
    @WithMockUser(username = "customer@example.com", authorities = {"CUSTOMER"})
    void customerHasNoVendorMembership() {
        when(membershipService.access("customer@example.com"))
            .thenReturn(Optional.empty());
        
        var ctx = authContext.current();
        
        assertThat(ctx.isCustomer()).isTrue();
        assertThat(ctx.isVendorEmployee()).isFalse();
        assertThat(ctx.isPlatformUser()).isFalse();
        assertThat(ctx.tenantId()).isNull();
        assertThat(ctx.branchId()).isNull();
    }
    
    @Test
    @WithMockUser(username = "customer@example.com", authorities = {"CUSTOMER"})
    void requireVendorMembershipThrowsForCustomer() {
        when(membershipService.access("customer@example.com"))
            .thenReturn(Optional.empty());
        
        var ctx = authContext.current();
        
        assertThatThrownBy(() -> ctx.requireVendorMembership())
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
            .hasMessageContaining("Vendor membership required");
    }
    
    @Test
    @WithMockUser(username = "admin@platform.com", authorities = {"PLATFORM_SUPER_ADMIN"})
    void platformUserHasNoPlatformPermissions() {
        when(membershipService.access("admin@platform.com"))
            .thenReturn(Optional.empty());
        
        var ctx = authContext.current();
        
        assertThat(ctx.isPlatformUser()).isTrue();
        assertThat(ctx.isVendorEmployee()).isFalse();
        assertThat(ctx.isCustomer()).isFalse();
        assertThat(ctx.tenantId()).isNull();
        assertThat(ctx.branchId()).isNull();
    }
}
