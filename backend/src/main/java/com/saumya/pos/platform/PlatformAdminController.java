package com.saumya.pos.platform;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.tenant.TenantStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/platform")
public class PlatformAdminController {
    private final PlatformAdminService admin;private final PlatformAuditService audit;
    private final PlatformSupportService support;private final PlatformConfigurationService configuration;private final AuthorizationContext auth;
    public PlatformAdminController(PlatformAdminService admin,PlatformAuditService audit,PlatformSupportService support,PlatformConfigurationService configuration,AuthorizationContext auth){
        this.admin=admin;this.audit=audit;this.support=support;this.configuration=configuration;this.auth=auth;
    }
    public record TenantCreate(@NotBlank @Size(max=100) String slug,@NotBlank @Size(max=160) String name,@NotBlank @Size(max=180) String legalName,
        @Email @Size(max=100) String contactEmail,@Size(max=30) String contactPhone,@Size(max=40) String taxIdentifier,@Size(max=500) String address,
        @NotBlank @Email @Size(max=100) String initialVendorAdminUsername){}
    public record TenantUpdate(@NotBlank @Size(max=160) String name,@NotBlank @Size(max=180) String legalName,@Email @Size(max=100) String contactEmail,
        @Size(max=30) String contactPhone,@Size(max=40) String taxIdentifier,@Size(max=500) String address){}
    public record LifecycleChange(@NotNull TenantStatus status,@NotBlank @Size(max=500) String reason){}
    public record SupportRequest(@Positive long tenantId,@NotBlank @Size(max=500) String purpose,@NotBlank @Size(max=120) String ticketReference,@NotNull Instant expiresAt){}
    public record SupportRevoke(@NotBlank @Size(max=500) String reason){}
    public record ConfigurationUpdate(@NotBlank @Size(max=100) String key,@NotBlank @Size(max=60) String category,String value,boolean sensitive,String secretReference){}
    private AuthorizationContext.Context require(String permission){var ctx=auth.current();if(!ctx.isPlatformUser())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform identity required");ctx.requirePermission(permission);return ctx;}
    private String correlation(String id){return id==null||id.isBlank()?null:id.strip().substring(0,Math.min(id.strip().length(),100));}
    @GetMapping("/dashboard") public Object dashboard(){require("PLATFORM_ANALYTICS_VIEW");return admin.dashboard();}
    @GetMapping("/tenants") public Object tenants(@RequestParam(required=false) String q,@RequestParam(required=false) TenantStatus status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){require("PLATFORM_TENANT_UPDATE");return admin.tenants(q,status,page,size);}
    @PostMapping("/tenants") public ResponseEntity<?> create(@Valid @RequestBody TenantCreate body,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        var actor=require("PLATFORM_TENANT_CREATE");var result=admin.onboard(new TenantLifecycleService.Registration(body.slug(),body.name(),body.legalName(),body.contactEmail(),body.contactPhone(),body.taxIdentifier(),body.address(),body.initialVendorAdminUsername()),actor.username(),correlation(correlation));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(result);
    }
    @PatchMapping("/tenants/{id}") public Object update(@PathVariable long id,@Valid @RequestBody TenantUpdate body,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        var actor=require("PLATFORM_TENANT_UPDATE");return admin.update(id,new TenantLifecycleService.ProfileUpdate(body.name(),body.legalName(),body.contactEmail(),body.contactPhone(),body.taxIdentifier(),body.address()),actor.username(),correlation(correlation));
    }
    @PostMapping("/tenants/{id}/lifecycle") public Object lifecycle(@PathVariable long id,@Valid @RequestBody LifecycleChange body,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        String permission=switch(body.status()){case SUSPENDED->"PLATFORM_TENANT_SUSPEND";case CLOSED->"PLATFORM_TENANT_CLOSE";default->"PLATFORM_TENANT_UPDATE";};
        var actor=require(permission);return admin.transition(id,body.status(),body.reason(),actor.username(),correlation(correlation));
    }
    @GetMapping("/audit") public Object audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) Long tenantId){require("PLATFORM_AUDIT_VIEW");return audit.search(page,size,tenantId);}
    @GetMapping("/support-access") public Object support(){require("PLATFORM_SUPPORT_ACCESS");return support.recent();}
    @PostMapping("/support-access") public ResponseEntity<?> grant(@Valid @RequestBody SupportRequest body,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        var actor=require("PLATFORM_SUPPORT_ACCESS");return ResponseEntity.status(HttpStatus.CREATED).body(support.grant(body.tenantId(),actor.username(),body.purpose(),body.ticketReference(),body.expiresAt(),correlation(correlation)));
    }
    @PostMapping("/support-access/{id}/revoke") public Object revoke(@PathVariable long id,@Valid @RequestBody SupportRevoke body,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){var actor=require("PLATFORM_SUPPORT_ACCESS");return support.revoke(id,actor.username(),body.reason(),correlation(correlation));}
    @GetMapping("/configuration") public Object settings(){require("PLATFORM_CONFIG_MANAGE");return configuration.list();}
    @PutMapping("/configuration") public Object put(@Valid @RequestBody ConfigurationUpdate body,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        var actor=require("PLATFORM_CONFIG_MANAGE");return configuration.put(new PlatformConfigurationService.Update(body.key(),body.category(),body.value(),body.sensitive(),body.secretReference()),actor.username(),correlation(correlation));
    }
}
