package com.saumya.pos.returns;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class RefundHistoryController {
    private final RefundHistoryService service;
    
    public RefundHistoryController(RefundHistoryService service) {
        this.service = service;
    }
    
    @GetMapping("/api/v1/vendor/pos/refunds/history")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW')")
    Object history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "") String reference,
            @RequestParam(defaultValue = "") String returnId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return service.history(from, to, reference, returnId, page, size);
    }
}
