package com.saumya.pos.returns;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class RefundReportController {
    private final RefundReportService service;
    
    public RefundReportController(RefundReportService service) {
        this.service = service;
    }
    
    @GetMapping("/api/v1/vendor/pos/reports/cash-refunds")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REPORT_VIEW') and hasAuthority('POS_REFUND_VIEW')")
    Object report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.report(from, to);
    }
}
