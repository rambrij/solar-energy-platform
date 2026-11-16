package com.saumya.pos.platform;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.subscription.SubscriptionPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController @RequestMapping("/api/v1/platform/subscription-plans")
public class PlatformSubscriptionController {
    private final SubscriptionPlanService plans;private final PlatformAuditService audit;private final AuthorizationContext auth;
    public PlatformSubscriptionController(SubscriptionPlanService plans,PlatformAuditService audit,AuthorizationContext auth){this.plans=plans;this.audit=audit;this.auth=auth;}
    public record PlanRequest(@NotBlank @Size(max=80) String planKey,@NotBlank @Size(max=120) String displayName,
        @NotNull @DecimalMin("0.00") BigDecimal price,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
        @NotBlank String billingInterval,@Min(1) int maxUsers,@Min(0) long maxStorageMb,@Min(1) int maxBranches,
        @Min(0) int maxWarehouses,@NotBlank String entitlements,@Min(0) @Max(90) int graceDays,
        @NotBlank String taxMetadata,@NotBlank @Size(max=500) String invoiceDescription){}
    public record ActiveRequest(boolean active){}
    private String actor(String permission){var c=auth.current();if(!c.isPlatformUser())throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);c.requirePermission(permission);return c.username();}
    private SubscriptionPlanService.PlanInput input(PlanRequest p){return new SubscriptionPlanService.PlanInput(p.planKey(),p.displayName(),p.price(),p.currency(),p.billingInterval(),p.maxUsers(),p.maxStorageMb(),p.maxBranches(),p.maxWarehouses(),p.entitlements(),p.graceDays(),p.taxMetadata(),p.invoiceDescription());}
    @GetMapping public Object list(){actor("PLATFORM_SUBSCRIPTION_VIEW");return plans.list();}
    @PostMapping public ResponseEntity<?> create(@Valid @RequestBody PlanRequest request,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        String user=actor("PLATFORM_PLAN_MANAGE");var result=plans.create(input(request),user,correlation);audit.record(user,"SUBSCRIPTION_PLAN_CREATED","SUBSCRIPTION_PLAN",Long.toString(result.id()),null,"Plan version 1 created",correlation);return ResponseEntity.status(CREATED).body(result);
    }
    @PutMapping("/{id}/versions") public Object revise(@PathVariable long id,@Valid @RequestBody PlanRequest request,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        String user=actor("PLATFORM_PLAN_MANAGE");var result=plans.revise(id,input(request),user,correlation);audit.record(user,"SUBSCRIPTION_PLAN_VERSION_CREATED","SUBSCRIPTION_PLAN",Long.toString(id),null,"New immutable price/entitlement version created",correlation);return result;
    }
    @PatchMapping("/{id}/active") public Object active(@PathVariable long id,@RequestBody ActiveRequest request,@RequestHeader(value="X-Correlation-ID",required=false) String correlation){
        String user=actor("PLATFORM_PLAN_MANAGE");var result=plans.setActive(id,request.active(),user,correlation);audit.record(user,request.active()?"SUBSCRIPTION_PLAN_ENABLED":"SUBSCRIPTION_PLAN_DISABLED","SUBSCRIPTION_PLAN",Long.toString(id),null,"Plan availability changed",correlation);return result;
    }
}
