package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.DiscountPolicyService;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vendor/pos/discount-policy")
public class DiscountPolicyController {
 private final DiscountPolicyService policies;private final PosService retail;
 public DiscountPolicyController(DiscountPolicyService policies,PosService retail){this.policies=policies;this.retail=retail;}
 public record Policy(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal maximumPercent){}
 @GetMapping @PreAuthorize("hasAuthority('POS_VIEW')") public Policy view(Principal actor){return new Policy(policies.maximum(retail.scope(actor.getName()).tenant()));}
 @org.springframework.transaction.annotation.Transactional
 @PostMapping @PreAuthorize("hasAuthority('POS_DISCOUNT_OVERRIDE')") public Policy configure(Principal actor,@Valid @RequestBody Policy request){var scope=retail.scope(actor.getName());policies.configure(scope.tenant(),request.maximumPercent());retail.draftAudit(scope,"DISCOUNT_POLICY_UPDATE",Long.toString(scope.tenant()));return request;}
}
