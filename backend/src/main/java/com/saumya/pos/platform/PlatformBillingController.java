package com.saumya.pos.platform;

import com.saumya.pos.platform.InvoiceMath.Interval;
import com.saumya.pos.platform.SubscriptionLifecycle.State;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Subscription plans, tenant subscriptions, invoices and platform-merchant payments. */
@RestController @RequestMapping("/api/v1/platform")
public class PlatformBillingController {
    public record TermsRequest(Interval interval,BigDecimal price,String currency,BigDecimal taxRate,Boolean taxInclusive,Integer graceDays,Integer maxUsers,Integer maxBranches,Integer maxProducts,List<String> features){
        PlanService.Terms terms(){return new PlanService.Terms(interval,price,currency,taxRate==null?BigDecimal.ZERO:taxRate,Boolean.TRUE.equals(taxInclusive),graceDays==null?0:graceDays,maxUsers,maxBranches,maxProducts,features==null?List.of():features);}
    }
    public record PlanRequest(String code,String name,TermsRequest terms) {}
    public record ActiveRequest(boolean active) {}
    public record AssignRequest(String planCode,Integer version) {}
    public record SubscriptionStatusRequest(State status,String reason) {}
    public record PaymentRequest(BigDecimal amount,String method,String reference,Boolean succeeded) {}
    private final PlanService plans;private final SubscriptionAdminService subscriptions;
    public PlatformBillingController(PlanService plans,SubscriptionAdminService subscriptions){this.plans=plans;this.subscriptions=subscriptions;}

    @GetMapping("/plans") @PreAuthorize("hasAnyAuthority('PLATFORM_PLAN_MANAGE','PLATFORM_SUBSCRIPTION_VIEW')") Object plans(){return plans.list();}
    @PostMapping("/plans") @PreAuthorize("hasAuthority('PLATFORM_PLAN_MANAGE')")
    Object createPlan(Authentication auth,@RequestBody PlanRequest r){return plans.create(auth.getName(),r.code(),r.name(),r.terms()==null?null:r.terms().terms());}
    @PostMapping("/plans/{code}/versions") @PreAuthorize("hasAuthority('PLATFORM_PLAN_MANAGE')")
    Object addVersion(Authentication auth,@PathVariable String code,@RequestBody TermsRequest r){return plans.addVersion(auth.getName(),code,r.terms());}
    @PostMapping("/plans/{code}/active") @PreAuthorize("hasAuthority('PLATFORM_PLAN_MANAGE')")
    Object setActive(Authentication auth,@PathVariable String code,@RequestBody ActiveRequest r){return plans.setActive(auth.getName(),code,r.active());}

    @GetMapping("/tenants/{id}/subscriptions") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_VIEW')") Object forTenant(@PathVariable long id){return subscriptions.forTenant(id);}
    @PostMapping("/tenants/{id}/subscriptions") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_ADJUST')")
    Object assign(Authentication auth,@PathVariable long id,@RequestBody AssignRequest r){return subscriptions.assign(auth.getName(),id,r.planCode(),r.version());}
    @PostMapping("/tenants/{id}/subscriptions/status") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_ADJUST')")
    Object transition(Authentication auth,@PathVariable long id,@RequestBody SubscriptionStatusRequest r){
        if(r.status()==null)throw Problems.invalid("Target status is required");
        return subscriptions.transition(auth.getName(),id,r.status(),r.reason());
    }
    @PostMapping("/tenants/{id}/subscriptions/invoices") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_ADJUST')")
    Object issueInvoice(Authentication auth,@PathVariable long id){return subscriptions.issueInvoice(auth.getName(),id);}
    @PostMapping("/subscription-invoices/{invoiceId}/payments") @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_ADJUST')")
    Object recordPayment(Authentication auth,@PathVariable String invoiceId,@RequestBody PaymentRequest r){
        if(r.succeeded()==null)throw Problems.invalid("succeeded is required");
        return subscriptions.recordPayment(auth.getName(),invoiceId,r.amount(),r.method(),r.reference(),r.succeeded());
    }
}
