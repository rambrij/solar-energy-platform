package com.saumya.pos.checkout;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class PaymentCheckoutController {
 private final PaymentCheckoutService payments;
 public PaymentCheckoutController(PaymentCheckoutService payments){this.payments=payments;}
 public record Start(@NotBlank @Pattern(regexp="UPI|CARD") String method){}
 public record Confirm(@NotBlank @Size(max=36) String attemptId){}
 @GetMapping("/api/v1/customer/checkout/drafts/{id}/payment") @PreAuthorize("hasAuthority('CUSTOMER')")
 Object customerStatus(Principal p,@PathVariable String id){return payments.status(p.getName(),id,true);}
 @PostMapping("/api/v1/customer/checkout/drafts/{id}/payment") @PreAuthorize("hasAuthority('CUSTOMER')")
 Object customerStart(Principal p,@PathVariable String id,@Valid @RequestBody Start r){return payments.start(p.getName(),id,true,r.method());}
 @PostMapping("/api/v1/customer/checkout/drafts/{id}/payment/mock-confirm") @PreAuthorize("hasAuthority('CUSTOMER')")
 Object customerConfirm(Principal p,@PathVariable String id,@Valid @RequestBody Confirm r){return payments.confirm(p.getName(),id,true,r.attemptId());}
 @GetMapping("/api/v1/vendor/pos/drafts/{id}/payment") @PreAuthorize("hasAuthority('POS_VIEW')")
 Object posStatus(Principal p,@PathVariable String id){return payments.status(p.getName(),id,false);}
 @PostMapping("/api/v1/vendor/pos/drafts/{id}/payment") @PreAuthorize("hasAuthority('POS_SALE_CREATE') and hasAuthority('POS_TENDER_EXTERNAL_RECORD')")
 Object posStart(Principal p,@PathVariable String id,@Valid @RequestBody Start r){return payments.start(p.getName(),id,false,r.method());}
 @PostMapping("/api/v1/vendor/pos/drafts/{id}/payment/unknown") @PreAuthorize("hasAuthority('POS_TENDER_EXTERNAL_RECORD')")
 Object posUnknown(Principal p,@PathVariable String id,@Valid @RequestBody Confirm r){return payments.unknown(p.getName(),id,r.attemptId());}
 @GetMapping("/api/v1/vendor/pos/payments/awaiting-review") @PreAuthorize("hasAuthority('POS_TENDER_MANUAL_VERIFY')")
 Object awaitingReview(Principal p){return payments.awaitingReview(p.getName());}
 @GetMapping("/api/v1/vendor/pos/payments/review/{id}") @PreAuthorize("hasAuthority('POS_TENDER_MANUAL_VERIFY')")
 Object reviewStatus(Principal p,@PathVariable String id){return payments.reviewStatus(p.getName(),id);}
 @PostMapping("/api/v1/vendor/pos/payments/review/{id}") @PreAuthorize("hasAuthority('POS_TENDER_MANUAL_VERIFY')")
 Object review(Principal p,@PathVariable String id,@RequestBody PaymentCheckoutService.Review r){return payments.review(p.getName(),id,r);}
}
