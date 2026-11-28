package com.saumya.pos.checkout;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only vendor-wide report endpoint; tenant scope is always taken from the authenticated membership. */
@RestController
@RequestMapping("/api/v1/vendor/reports/overview")
public class VendorTenantReportController {
    private final VendorTenantReportService service;
    public VendorTenantReportController(VendorTenantReportService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('POS_REPORT_VIEW')")
    public VendorTenantReportService.Report report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
