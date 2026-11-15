package com.saumya.pos.retail;
import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/vendor/pos")
class PosController {
    private final PosService service;
    PosController(PosService service) { this.service=service; }
    
    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }
    
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('POS_VIEW')")
    Object products() {
        return service.products(service.scope());
    }
    
    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('POS_SESSION_OPEN')")
    Object open(@Valid @RequestBody PosService.OpenSession r) {
        return service.open(service.scope(), r);
    }
    
    @GetMapping("/sessions/active")
    @PreAuthorize("hasAuthority('POS_VIEW')")
    Object activeSessions() {
        return service.activeSessions(service.scope());
    }
    
    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('POS_VIEW')")
    Object sessionDetails(@PathVariable String id) {
        return service.sessionDetails(service.scope(), id);
    }
    
    @PostMapping("/sessions/{id}/resume")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_SESSION_OPEN')")
    Object resume(@PathVariable String id, @Valid @RequestBody ResumeSession request) {
        return service.resume(service.scope(), id);
    }
    record ResumeSession() {}
    record CloseSession(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=12,fraction=2) java.math.BigDecimal countedCash,
                        @jakarta.validation.constraints.Size(max=250) String reason,
                        @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F-]{36}") String requestId) {}
    
    @PostMapping("/sessions/{id}/close")
    @PreAuthorize("hasAuthority('POS_SESSION_CLOSE')")
    Object close(@PathVariable String id, @Valid @RequestBody CloseSession r) {
        return service.close(service.scope(), id, r.countedCash(), r.reason(), r.requestId());
    }
    
    @GetMapping("/sessions/{id}/close-status")
    @PreAuthorize("hasAuthority('POS_VIEW')")
    Object closeStatus(@PathVariable String id) {
        return service.closeStatus(service.scope(), id);
    }
    
    @GetMapping("/sessions/pending-variances")
    @PreAuthorize("hasAuthority('POS_SHIFT_VARIANCE_APPROVE')")
    Object pendingVariances() {
        return service.pendingVariances(service.scope());
    }
    record VarianceReview(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=36) String requestId,
                          @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=250) String reason) {}
    
    @PostMapping("/sessions/{id}/approve-variance")
    @PreAuthorize("hasAuthority('POS_SHIFT_VARIANCE_APPROVE')")
    Object approveVariance(@PathVariable String id, @Valid @RequestBody VarianceReview r) {
        return service.reviewVariance(service.scope(), id, r.requestId(), r.reason(), PosService.ReviewAction.APPROVE);
    }
    
    @PostMapping("/sessions/{id}/return-for-correction")
    @PreAuthorize("hasAuthority('POS_SHIFT_VARIANCE_APPROVE')")
    Object returnVariance(@PathVariable String id, @Valid @RequestBody VarianceReview r) {
        return service.reviewVariance(service.scope(), id, r.requestId(), r.reason(), PosService.ReviewAction.RETURN_FOR_CORRECTION);
    }
    
    @PostMapping("/sessions/{id}/cash-movements")
    @PreAuthorize("hasAuthority('POS_' + #request.type().name())")
    Object cashMovement(
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PosService.CashMovement request
    ) {
        return service.cashMovement(service.scope(), id, key, request);
    }
    
    @GetMapping("/sessions/{id}/reconciliation")
    @PreAuthorize("hasAuthority('POS_VIEW') or hasAuthority('POS_REPORT_VIEW')")
    Object reconciliation(@PathVariable String id) {
        return service.reconciliation(service.scope(), id);
    }
}
