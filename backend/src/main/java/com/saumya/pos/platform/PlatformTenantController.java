package com.saumya.pos.platform;

import com.saumya.pos.identity.PlatformPermissions;
import com.saumya.pos.tenant.TenantLifecycleService.NewTenant;
import com.saumya.pos.tenant.TenantLifecycleService.TenantProfile;
import com.saumya.pos.tenant.TenantStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Tenant directory, onboarding, Vendor Admin provisioning and lifecycle. Platform permissions only; vendor roles can never hold them. */
@RestController @RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {
    public record TenantRequest(String slug,String name,String legalName,String contactEmail,String contactPhone,String address,String taxId) {}
    public record ProfileRequest(String name,String legalName,String contactEmail,String contactPhone,String address,String taxId) {}
    public record AdminRequest(String username) {}
    public record StatusRequest(TenantStatus status,String reason) {}
    private final TenantAdminService tenants;private final PlatformAuditService audit;
    public PlatformTenantController(TenantAdminService tenants,PlatformAuditService audit){this.tenants=tenants;this.audit=audit;}

    @GetMapping @PreAuthorize("hasAnyAuthority('PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_ANALYTICS_VIEW')")
    Object list(@RequestParam(required=false) TenantStatus status,@RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return tenants.list(status,q,page,size);}

    @GetMapping("/{id}") @PreAuthorize("hasAnyAuthority('PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_ANALYTICS_VIEW')")
    Object get(@PathVariable long id){return tenants.get(id);}

    @PostMapping @PreAuthorize("hasAuthority('PLATFORM_TENANT_CREATE')")
    ResponseEntity<Object> onboard(Authentication auth,@RequestBody TenantRequest r){
        var result=tenants.onboard(auth.getName(),new NewTenant(r.slug(),r.name(),r.legalName(),r.contactEmail(),r.contactPhone(),r.address(),r.taxId()));
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PLATFORM_TENANT_UPDATE')")
    Object update(Authentication auth,@PathVariable long id,@RequestBody ProfileRequest r){return tenants.updateProfile(auth.getName(),id,new TenantProfile(r.name(),r.legalName(),r.contactEmail(),r.contactPhone(),r.address(),r.taxId()));}

    /** The temporary password appears once, in this response only, and is never stored or retrievable. */
    @PostMapping("/{id}/vendor-admin") @PreAuthorize("hasAuthority('PLATFORM_TENANT_CREATE')")
    ResponseEntity<Object> vendorAdmin(Authentication auth,@PathVariable long id,@RequestBody AdminRequest r){
        var result=tenants.provisionVendorAdmin(auth.getName(),id,r.username());
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).cacheControl(CacheControl.noStore()).body(result);
    }
    /** Suspension and closure need their own permissions; every other lifecycle step needs PLATFORM_TENANT_UPDATE. */
    @PostMapping("/{id}/status") @PreAuthorize("hasAnyAuthority('PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE')")
    Object status(Authentication auth,@PathVariable long id,@RequestBody StatusRequest r){
        if(r.status()==null)throw Problems.invalid("Target status is required");
        String needed=switch(r.status()){case SUSPENDED->PlatformPermissions.TENANT_SUSPEND;case CLOSED->PlatformPermissions.TENANT_CLOSE;default->PlatformPermissions.TENANT_UPDATE;};
        if(auth.getAuthorities().stream().noneMatch(a->needed.equals(a.getAuthority()))){
            audit.recordDenied(auth.getName(),"TENANT_"+r.status().name(),"TENANT",String.valueOf(id),id,"Missing "+needed);
            throw new org.springframework.security.access.AccessDeniedException("Missing "+needed);
        }
        return tenants.transition(auth.getName(),id,r.status(),r.reason());
    }
}
