package com.saumya.pos.checkout;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class PaymentCheckoutController {
 private final PaymentCheckoutService payments;
 private final TenderReviewService reviews;
 public PaymentCheckoutController(PaymentCheckoutService payments,TenderReviewService reviews){this.payments=payments;this.reviews=reviews;}
 public record Start(@NotBlank @Pattern(regexp="UPI|CARD") String method){}
 public record Confirm(@NotBlank @Size(max=36) String attemptId){}
 @GetMapping("/api/v1/customer/checkout/drafts/{id}/payment") @PreAuthorize("hasAuthority('CUSTOMER')")
 Object customerStatus(Principal p,@PathVariable String id){return payments.status(p.getName(),id);}
 @PostMapping("/api/v1/customer/checkout/drafts/{id}/payment") @PreAuthorize("hasAuthority('CUSTOMER')")
 Object customerStart(Principal p,@PathVariable String id,@Valid @RequestBody Start r){return payments.start(p.getName(),id,r.method());}
 @PostMapping("/api/v1/customer/checkout/drafts/{id}/payment/mock-confirm") @PreAuthorize("hasAuthority('CUSTOMER')")
 Object customerConfirm(Principal p,@PathVariable String id,@Valid @RequestBody Confirm r){return payments.confirm(p.getName(),id,r.attemptId());}
 @GetMapping("/api/v1/vendor/pos/sales/{id}/tenders") @PreAuthorize("hasAuthority('POS_VIEW')")
 Object tenderStatus(Principal p,@PathVariable String id){return payments.tenderStatus(p.getName(),id);}
 @PostMapping("/api/v1/vendor/pos/sales/{id}/tenders") @PreAuthorize("hasAuthority('POS_SALE_CREATE') and (#r.method() == 'CASH' ? hasAuthority('POS_TENDER_CASH_RECORD') : hasAuthority('POS_TENDER_EXTERNAL_RECORD'))")
 Object tender(Principal p,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PaymentCheckoutService.TenderRequest r){return payments.tender(p.getName(),id,key,r);}
 @PostMapping("/api/v1/vendor/pos/sales/{id}/finalize") @PreAuthorize("hasAuthority('POS_SALE_CREATE')")
 Object finalizeTenders(Principal p,@PathVariable String id,@RequestHeader("Idempotency-Key") String key){if(!key.matches("[A-Za-z0-9_-]{8,80}"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Valid Idempotency-Key required");return payments.finalizeTenders(p.getName(),id);}
 @PostMapping("/api/v1/vendor/pos/sales/{id}/tenders/{attemptId}/unknown") @PreAuthorize("hasAuthority('POS_TENDER_EXTERNAL_RECORD')")
 Object posUnknown(Principal p,@PathVariable String id,@PathVariable String attemptId){return payments.unknown(p.getName(),id,attemptId);}
 @GetMapping("/api/v1/vendor/pos/payments/awaiting-review") @PreAuthorize("hasAuthority('POS_TENDER_MANUAL_VERIFY')")
 Object awaitingReview(Principal p){return payments.awaitingReview(p.getName());}
 @GetMapping("/api/v1/vendor/pos/payments/review/{id}") @PreAuthorize("hasAuthority('POS_TENDER_MANUAL_VERIFY')")
 Object reviewStatus(Principal p,@PathVariable String id){return payments.reviewStatus(p.getName(),id);}
 @PostMapping("/api/v1/vendor/pos/payments/review/{id}") @PreAuthorize("hasAuthority('POS_TENDER_MANUAL_VERIFY')")
 Object review(Principal p,@PathVariable String id,@RequestBody PaymentCheckoutService.Review r){return reviews.review(p.getName(),id,r);}
}
