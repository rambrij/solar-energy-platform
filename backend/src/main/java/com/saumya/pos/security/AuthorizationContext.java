package com.saumya.pos.security;

import com.saumya.pos.membership.MembershipService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides authorization context resolution from authenticated principal.
 * Never trust client-supplied tenant_id, branch_id, cashier_user_id, or session_id.
 * Always resolve context from JWT claims and server-side membership validation.
 */
@Component
public class AuthorizationContext {
    private final MembershipService membershipService;

    public AuthorizationContext(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /**
     * Represents the resolved authorization context for the current authenticated user.
     */
    public record Context(
        String username,
        Long tenantId,
        Long branchId,
        Set<String> permissions,
        boolean isPlatformUser,
        boolean isCustomer,
        boolean isVendorEmployee
    ) {
        public void requirePermission(String permission) {
            if (!permissions.contains(permission)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Missing required permission: " + permission);
            }
        }

        public void requirePermissions(String... requiredPermissions) {
            for (String permission : requiredPermissions) {
                requirePermission(permission);
            }
        }

        public void requireVendorMembership() {
            if (tenantId == null || !isVendorEmployee) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Active vendor membership required");
            }
        }

        public void requireTenant(Long requestedTenantId) {
            if (requestedTenantId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Tenant ID required");
            }
            if (!requestedTenantId.equals(tenantId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Cross-tenant access denied");
            }
        }

        public void requireBranch(Long requestedBranchId) {
            if (requestedBranchId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Branch ID required");
            }
            if (!requestedBranchId.equals(branchId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Branch assignment mismatch - cashier not assigned to requested branch");
            }
        }

        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        public boolean hasAnyPermission(String... permissions) {
            for (String permission : permissions) {
                if (this.permissions.contains(permission)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Resolve authorization context from the current authenticated principal.
     * NEVER accept tenant_id, branch_id, or other context from request body/query/header.
     */
    public Context current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String username = auth.getName();
        Set<String> permissions = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        boolean isCustomer = permissions.contains("CUSTOMER");
        boolean isPlatform = permissions.stream().anyMatch(p -> p.startsWith("PLATFORM_"));

        // Platform users have no tenant membership
        if (isPlatform) {
            return new Context(username, null, null, permissions, true, false, false);
        }

        // Customer users have no vendor membership
        if (isCustomer && !isPlatform) {
            var membership = membershipService.access(username);
            if (membership.isEmpty()) {
                // Pure customer, no vendor membership
                return new Context(username, null, null, permissions, false, true, false);
            }
            // Customer with vendor membership - use vendor context for vendor operations
            var access = membership.get();
            return new Context(
                access.username(),
                access.tenant(),
                access.branch(), 
                Set.copyOf(access.permissions()), 
                false, 
                true, 
                true
            );
        }

        // Vendor employee - must have active membership
        var membership = membershipService.access(username);
        if (membership.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Active vendor membership required");
        }

        var access = membership.get();
        return new Context(
            access.username(),
            access.tenant(),
            access.branch(),
            Set.copyOf(access.permissions()),
            false,
            false,
            true
        );
    }

    /**
     * Convenience method to get current user's tenant ID.
     * Throws exception if user has no tenant membership.
     */
    public Long currentTenantId() {
        Context ctx = current();
        if (ctx.tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Tenant membership required");
        }
        return ctx.tenantId;
    }

    /**
     * Convenience method to get current user's branch ID.
     * Throws exception if user has no branch assignment.
     */
    public Long currentBranchId() {
        Context ctx = current();
        if (ctx.branchId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Branch assignment required");
        }
        return ctx.branchId;
    }

    /**
     * Verify that the requested tenant ID matches the user's tenant membership.
     * Use this to validate client-supplied tenant IDs in request bodies.
     */
    public void validateTenantAccess(Long requestedTenantId) {
        current().requireTenant(requestedTenantId);
    }

    /**
     * Verify that the requested branch ID matches the user's branch assignment.
     * Use this for POS operations requiring branch scope.
     */
    public void validateBranchAccess(Long requestedBranchId) {
        current().requireBranch(requestedBranchId);
    }
}
