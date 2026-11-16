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

@Component
public class AuthorizationContext {
    private final MembershipService membershipService;
    public AuthorizationContext(MembershipService membershipService){this.membershipService=membershipService;}
    public record Context(String username,Long tenantId,Long branchId,Set<String> permissions,
        boolean isPlatformUser,boolean isCustomer,boolean isVendorEmployee){
        public void requirePermission(String permission){if(!permissions.contains(permission))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Missing required permission: "+permission);}
        public void requirePermissions(String... required){for(String p:required)requirePermission(p);}
        public void requireVendorMembership(){if(tenantId==null||!isVendorEmployee||isCustomer||isPlatformUser)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required");}
        public void requireTenant(Long requested){if(requested==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tenant ID required");if(!requested.equals(tenantId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cross-tenant access denied");}
        public void requireBranch(Long requested){if(requested==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Branch ID required");if(!requested.equals(branchId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch assignment mismatch");}
        public boolean hasPermission(String permission){return permissions.contains(permission);}
        public boolean hasAnyPermission(String... required){for(String p:required)if(permissions.contains(p))return true;return false;}
    }
    public Context current(){
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||"anonymousUser".equals(auth.getPrincipal()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
        String username=auth.getName();Set<String> permissions=auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean customer=permissions.contains("CUSTOMER"),platform=permissions.stream().anyMatch(p->p.startsWith("PLATFORM_"));
        if(platform)return new Context(username,null,null,permissions,true,false,false);
        if(customer){
            var membership=membershipService.access(username);
            if(membership.isEmpty())return new Context(username,null,null,permissions,false,true,false);
            return new Context(username,membership.get().tenant(),membership.get().branch(),Set.copyOf(membership.get().permissions()),false,true,true);
        }
        var membership=membershipService.access(username);
        if(membership.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required");
        var access=membership.get();
        return new Context(access.username(),access.tenant(),access.branch(),Set.copyOf(access.permissions()),false,false,true);
    }
    public Long currentTenantId(){var ctx=current();if(ctx.tenantId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant membership required");return ctx.tenantId();}
    public Long currentBranchId(){var ctx=current();if(ctx.branchId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch assignment required");return ctx.branchId();}
    public void validateTenantAccess(Long id){current().requireTenant(id);}
    public void validateBranchAccess(Long id){current().requireBranch(id);}
}
