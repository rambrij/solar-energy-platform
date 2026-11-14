package com.saumya.pos.retail;
import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/vendor/pos")
class PosController {
    private final PosService service;
    PosController(PosService service) { this.service=service; }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken()); }
    @GetMapping("/products") @PreAuthorize("hasAuthority('POS_VIEW')")
    Object products(Principal p) { return service.products(service.scope(p.getName())); }
    @PostMapping("/sessions") @PreAuthorize("hasAuthority('POS_SESSION_OPEN')")
    Object open(Principal p,@Valid @RequestBody PosService.OpenSession r) { return service.open(service.scope(p.getName()),r); }
    @GetMapping("/sessions/active") @PreAuthorize("hasAuthority('POS_VIEW')")
    Object activeSessions(Principal p) { return service.activeSessions(service.scope(p.getName())); }
    @GetMapping("/sessions/{id}") @PreAuthorize("hasAuthority('POS_VIEW')")
    Object sessionDetails(Principal p,@PathVariable String id) { return service.sessionDetails(service.scope(p.getName()),id); }
    @PostMapping("/sessions/{id}/resume")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_SESSION_OPEN')")
    Object resume(Principal p,@PathVariable String id,@Valid @RequestBody ResumeSession request) {
        return service.resume(service.scope(p.getName()),id);
    }
    record ResumeSession() {}
    record CloseSession(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=12,fraction=2) java.math.BigDecimal countedCash,
                        @jakarta.validation.constraints.Size(max=250) String reason,
                        @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F-]{36}") String requestId) {}
    @PostMapping("/sessions/{id}/close") @PreAuthorize("hasAuthority('POS_SESSION_CLOSE')")
    Object close(Principal p,@PathVariable String id,@Valid @RequestBody CloseSession r) { return service.close(service.scope(p.getName()),id,r.countedCash(),r.reason(),r.requestId()); }
    @GetMapping("/sessions/{id}/close-status") @PreAuthorize("hasAuthority('POS_VIEW')")
    Object closeStatus(Principal p,@PathVariable String id) { return service.closeStatus(service.scope(p.getName()),id); }
    @GetMapping("/sessions/pending-variances") @PreAuthorize("hasAuthority('POS_SHIFT_VARIANCE_APPROVE')")
    Object pendingVariances(Principal p) { return service.pendingVariances(service.scope(p.getName())); }
    record VarianceReview(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=36) String requestId,
                          @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=250) String reason) {}
    @PostMapping("/sessions/{id}/approve-variance") @PreAuthorize("hasAuthority('POS_SHIFT_VARIANCE_APPROVE')")
    Object approveVariance(Principal p,@PathVariable String id,@Valid @RequestBody VarianceReview r) {
        return service.reviewVariance(service.scope(p.getName()),id,r.requestId(),r.reason(),PosService.ReviewAction.APPROVE);
    }
    @PostMapping("/sessions/{id}/return-for-correction") @PreAuthorize("hasAuthority('POS_SHIFT_VARIANCE_APPROVE')")
    Object returnVariance(Principal p,@PathVariable String id,@Valid @RequestBody VarianceReview r) {
        return service.reviewVariance(service.scope(p.getName()),id,r.requestId(),r.reason(),PosService.ReviewAction.RETURN_FOR_CORRECTION);
    }
    @PostMapping("/sessions/{id}/cash-movements")
    @PreAuthorize("hasAuthority('POS_' + #request.type().name())")
    Object cashMovement(Principal p,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,
                        @Valid @RequestBody PosService.CashMovement request) {
        return service.cashMovement(service.scope(p.getName()),id,key,request);
    }
    @GetMapping("/sessions/{id}/reconciliation")
    @PreAuthorize("hasAuthority('POS_VIEW') or hasAuthority('POS_REPORT_VIEW')")
    Object reconciliation(Principal p,@PathVariable String id) {
        return service.reconciliation(service.scope(p.getName()),id);
    }
    @GetMapping("/receipts/{id}") @PreAuthorize("hasAuthority('POS_RECEIPT_VIEW')")
    Object receipt(Principal p,@PathVariable String id) { return service.receipt(service.scope(p.getName()),id); }
    @PostMapping("/receipts/{id}/reprint") @PreAuthorize("hasAuthority('POS_RECEIPT_REPRINT')")
    Object reprint(Principal p,@PathVariable String id) { return service.reprint(service.scope(p.getName()),id); }
}
