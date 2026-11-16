package com.saumya.pos.platform;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Dashboard, audit, global configuration and controlled support access. */
@RestController @RequestMapping("/api/v1/platform")
public class PlatformOperationsController {
    public record ConfigRequest(String value) {}
    public record SupportRequest(String reason,String ticket,Integer minutes) {}
    private final PlatformInsightService insight;private final PlatformAuditService audit;private final ConfigurationService config;private final SupportAccessService support;
    public PlatformOperationsController(PlatformInsightService insight,PlatformAuditService audit,ConfigurationService config,SupportAccessService support){this.insight=insight;this.audit=audit;this.config=config;this.support=support;}

    @GetMapping("/overview") @PreAuthorize("hasAuthority('PLATFORM_ANALYTICS_VIEW')") Object overview(){return insight.overview();}
    @GetMapping("/health") @PreAuthorize("hasAuthority('PLATFORM_ANALYTICS_VIEW')") Object health(){return insight.health();}
    @GetMapping("/audit") @PreAuthorize("hasAuthority('PLATFORM_AUDIT_VIEW')")
    Object audit(@RequestParam(required=false) String action,@RequestParam(required=false) Long tenantId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return audit.search(action,tenantId,page,size);}

    /** Values are masked; secret keys hold references only. Viewing needs analytics access, changing needs subscription-adjust (the keys configure the platform merchant account). */
    @GetMapping("/configuration") @PreAuthorize("hasAuthority('PLATFORM_ANALYTICS_VIEW')") Object configuration(){return config.list();}
    @PutMapping("/configuration/{key}") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_ADJUST')")
    Object setConfiguration(Authentication auth,@PathVariable String key,@RequestBody ConfigRequest r){return config.put(auth.getName(),key,r.value());}

    @GetMapping("/tenants/{id}/support-access") @PreAuthorize("hasAuthority('PLATFORM_SUPPORT_ACCESS')") Object grants(@PathVariable long id){return support.list(id);}
    @PostMapping("/tenants/{id}/support-access") @PreAuthorize("hasAuthority('PLATFORM_SUPPORT_ACCESS')")
    Object grant(Authentication auth,@PathVariable long id,@RequestBody SupportRequest r){return support.grant(auth.getName(),id,r.reason(),r.ticket(),r.minutes()==null?0:r.minutes());}
    @PostMapping("/support-access/{grantId}/revoke") @PreAuthorize("hasAuthority('PLATFORM_SUPPORT_ACCESS')") Object revoke(Authentication auth,@PathVariable String grantId){return support.revoke(auth.getName(),grantId);}
    @GetMapping("/support-access/{grantId}/vendor-audit") @PreAuthorize("hasAuthority('PLATFORM_SUPPORT_ACCESS')")
    Object read(Authentication auth,@PathVariable String grantId,@RequestParam(defaultValue="50") int limit){return support.readVendorAudit(auth.getName(),grantId,limit);}
}
