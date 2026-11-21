package com.saumya.pos.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuthorizationContext {
    public static final String ACTOR_CONTEXT_PLATFORM = "ACTOR_CONTEXT_PLATFORM";
    public static final String ACTOR_CONTEXT_VENDOR = "ACTOR_CONTEXT_VENDOR";
    public static final String ACTOR_CONTEXT_CUSTOMER = "ACTOR_CONTEXT_CUSTOMER";
    private final VendorMembershipResolver memberships;
    public AuthorizationContext(VendorMembershipResolver memberships){this.memberships=memberships;}

    public record Context(String username,Long tenantId,Long branchId,String membershipId,Set<String> permissions,
        boolean isPlatformUser,boolean isCustomer,boolean isVendorEmployee) {
        public void requirePermission(String permission){if(!permissions.contains(permission))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Missing required permission: "+permission);}
        public void requirePermissions(String... required){for(String p:required)requirePermission(p);}
        public void requireVendorMembership(){if(tenantId==null||membershipId==null||!isVendorEmployee||isCustomer||isPlatformUser)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required");}
        public void requireTenant(Long requested){if(requested==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tenant ID required");if(!requested.equals(tenantId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cross-tenant access denied");}
        public void requireBranch(Long requested){if(requested==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Branch ID required");if(!requested.equals(branchId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch assignment mismatch");}
        public boolean hasPermission(String permission){return permissions.contains(permission);}
        public boolean hasAnyPermission(String... required){for(String p:required)if(permissions.contains(p))return true;return false;}
    }

    public Context current() {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||"anonymousUser".equals(auth.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");

        String username=auth.getName();
        Set<String> authorities=auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        long knownMarkerCount=Set.of(ACTOR_CONTEXT_PLATFORM,ACTOR_CONTEXT_VENDOR,ACTOR_CONTEXT_CUSTOMER).stream()
            .filter(authorities::contains).count();
        long allMarkerCount=authorities.stream().filter(a->a.startsWith("ACTOR_CONTEXT_")).count();
        if(knownMarkerCount!=1||allMarkerCount!=1)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Exactly one explicit actor context is required");

        Set<String> permissions=authorities.stream()
            .filter(a->!a.startsWith("ACTOR_CONTEXT_"))
            .collect(Collectors.toUnmodifiableSet());

        if(authorities.contains(ACTOR_CONTEXT_PLATFORM)) {
            if(permissions.isEmpty()||permissions.stream().anyMatch(p->!p.startsWith("PLATFORM_")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform context cannot contain commercial permissions");
            if(auth.getDetails() instanceof ActorSessionDetails details
                &&(!details.actorType().equals("PLATFORM")
                    ||details.tenantId()!=null||details.membershipId()!=null))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform actor context is stale or inconsistent");
            return new Context(username,null,null,null,permissions,true,false,false);
        }

        if(authorities.contains(ACTOR_CONTEXT_CUSTOMER)) {
            if(!permissions.equals(Set.of("CUSTOMER")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer context cannot contain vendor or platform permissions");
            if(auth.getDetails() instanceof ActorSessionDetails details
                &&(!details.actorType().equals("CUSTOMER")
                    ||details.tenantId()!=null||details.membershipId()!=null))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer actor context is stale or inconsistent");
            return new Context(username,null,null,null,permissions,false,true,false);
        }

        if(permissions.stream().anyMatch(p->p.equals("CUSTOMER")||p.startsWith("PLATFORM_")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Vendor context cannot contain customer or platform permissions");
        var membership=memberships.resolve(username);
        if(membership.isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required");
        var access=membership.get();
        Set<String> activePermissions=Set.copyOf(access.permissions());
        // A principal may carry a subset of current grants, but never an invented or revoked grant.
        if(!activePermissions.containsAll(permissions))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Vendor permission context is stale or inconsistent");
        if(auth.getDetails() instanceof ActorSessionDetails details
            &&(!details.actorType().equals("VENDOR")
                ||!java.util.Objects.equals(details.tenantId(),access.tenant())
                ||!java.util.Objects.equals(details.membershipId(),access.id())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Vendor membership context is stale or inconsistent");
        return new Context(access.username(),access.tenant(),access.branch(),access.id(),permissions,false,false,true);
    }

    public Long currentTenantId(){var ctx=current();ctx.requireVendorMembership();return ctx.tenantId();}
    public Long currentBranchId(){var ctx=current();ctx.requireVendorMembership();if(ctx.branchId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch assignment required");return ctx.branchId();}
    public void validateTenantAccess(Long id){current().requireTenant(id);}
    public void validateBranchAccess(Long id){current().requireBranch(id);}
}
