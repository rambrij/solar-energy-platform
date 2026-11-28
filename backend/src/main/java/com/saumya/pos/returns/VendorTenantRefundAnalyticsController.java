package com.saumya.pos.returns;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only tenant-wide POS cash refund report. */
@RestController
@RequestMapping("/api/v1/vendor/reports/refunds")
public class VendorTenantRefundAnalyticsController {
    private final VendorTenantRefundAnalyticsService service;
    public VendorTenantRefundAnalyticsController(VendorTenantRefundAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('POS_REPORT_VIEW') and hasAuthority('POS_REFUND_VIEW')")
    public VendorTenantRefundAnalyticsService.Report report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
