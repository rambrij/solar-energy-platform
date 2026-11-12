package com.saumya.pos;
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
    record CloseSession(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=12,fraction=2) java.math.BigDecimal countedCash) {}
    @PostMapping("/sessions/{id}/close") @PreAuthorize("hasAuthority('POS_SESSION_CLOSE')")
    Object close(Principal p,@PathVariable String id,@Valid @RequestBody CloseSession r) { return service.close(service.scope(p.getName()),id,r.countedCash()); }
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
    @PostMapping("/sales") @PreAuthorize("hasAuthority('POS_SALE_CREATE') and hasAuthority('POS_TENDER_CASH_RECORD') and hasAuthority('POS_RECEIPT_VIEW')")
    Object sale(Principal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PosService.Checkout r) { return service.checkout(service.scope(p.getName()),key,r); }
    @GetMapping("/receipts/{id}") @PreAuthorize("hasAuthority('POS_RECEIPT_VIEW')")
    Object receipt(Principal p,@PathVariable String id) { return service.receipt(service.scope(p.getName()),id); }
    @PostMapping("/receipts/{id}/reprint") @PreAuthorize("hasAuthority('POS_RECEIPT_REPRINT')")
    Object reprint(Principal p,@PathVariable String id) { return service.reprint(service.scope(p.getName()),id); }
}
