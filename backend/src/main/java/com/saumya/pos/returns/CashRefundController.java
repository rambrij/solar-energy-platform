package com.saumya.pos.returns;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/vendor/pos/refunds")
@PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW')")
public class CashRefundController {
    private final CashRefundService service;
    
    public CashRefundController(CashRefundService service) {
        this.service = service;
    }
    
    @GetMapping
    Object list() {
        return service.list();
    }
    
    @GetMapping("/{id}")
    Object get(@PathVariable String id) {
        return service.get(id);
    }
    
    @GetMapping("/return/{id}")
    Object byReturn(@PathVariable String id) {
        return service.byReturn(id);
    }
    
    @GetMapping("/quote/{returnId}")
    @PreAuthorize(
            "hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW') and hasAuthority('POS_REFUND_APPROVE') and hasAuthority('POS_REFUND_CASH_RECORD')"
    )
    Object quote(@PathVariable String returnId, @RequestParam String sessionId) {
        return service.quote(returnId, sessionId);
    }
    
    @PostMapping
    @PreAuthorize(
            "hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW') and hasAuthority('POS_REFUND_APPROVE') and hasAuthority('POS_REFUND_CASH_RECORD')"
    )
    Object pay(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CashRefundService.Pay request) {
        return service.pay(key, request);
    }
    
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW') and hasAuthority('POS_REFUND_PRINT')")
    Object copy(@PathVariable String id) {
        return service.copy(id);
    }
}
