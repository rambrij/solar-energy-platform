package com.saumya.pos.platform;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.subscription.TenantSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/v1/platform")
public class PlatformTenantSubscriptionController {
    private final TenantSubscriptionService subscriptions;private final PlatformAuditService audit;private final AuthorizationContext auth;
    public PlatformTenantSubscriptionController(TenantSubscriptionService subscriptions,PlatformAuditService audit,AuthorizationContext auth){this.subscriptions=subscriptions;this.audit=audit;this.auth=auth;}
    public record CreateSubscription(@Positive long planId,@NotNull LocalDate periodStart,@NotNull LocalDate periodEnd,@NotBlank @Size(max=500) String reason){}
    public record ChangeStatus(@NotBlank String status,@NotBlank @Size(max=500) String reason,@PositiveOrZero Long expectedVersion){}
    private String actor(String permission){var c=auth.current();if(!c.isPlatformUser())throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);c.requirePermission(permission);return c.username();}
    @GetMapping("/subscriptions") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_VIEW')") public Object list(@RequestParam(required=false) Long tenantId){
        actor("PLATFORM_SUBSCRIPTION_VIEW");return tenantId==null?subscriptions.all():subscriptions.forTenant(tenantId);
    }
    @PostMapping("/tenants/{tenantId}/subscription") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_ADJUST')") public Object create(@PathVariable long tenantId,@Valid @RequestBody CreateSubscription body,
        @RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        String user=actor("PLATFORM_SUBSCRIPTION_ADJUST");
        var result=subscriptions.create(new TenantSubscriptionService.SubscriptionInput(tenantId,body.planId(),body.periodStart(),body.periodEnd(),body.reason()),user);
        audit.record(user,"TENANT_SUBSCRIPTION_CREATED","TENANT_SUBSCRIPTION",Long.toString(result.id()),tenantId,body.reason(),correlation);
        return result;
    }
    @PostMapping("/subscriptions/{id}/status") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_ADJUST')") public Object change(@PathVariable long id,@Valid @RequestBody ChangeStatus body,
        @RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        String user=actor("PLATFORM_SUBSCRIPTION_ADJUST");
        var result=subscriptions.transition(id,body.status(),body.reason(),user,body.expectedVersion());
        audit.record(user,"TENANT_SUBSCRIPTION_STATUS_CHANGED","TENANT_SUBSCRIPTION",Long.toString(id),result.tenantId(),body.reason(),correlation);
        return result;
    }
    @GetMapping("/subscriptions/{id}/events") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_VIEW')") public Object events(@PathVariable long id){actor("PLATFORM_SUBSCRIPTION_VIEW");return subscriptions.events(id);}
    @GetMapping("/subscriptions/{id}/billing-history") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_VIEW')") public Object billing(@PathVariable long id){actor("PLATFORM_SUBSCRIPTION_VIEW");return subscriptions.billingHistory(id);}
}
