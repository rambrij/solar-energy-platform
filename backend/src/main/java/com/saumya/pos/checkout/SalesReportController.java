package com.saumya.pos.checkout;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class SalesReportController {
    private final SalesReportService reports;
    public SalesReportController(SalesReportService reports){this.reports=reports;}
    @GetMapping("/api/vendor/pos/reports/sales")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REPORT_VIEW')")
    Object sales(Principal p,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="ALL") String channel){
        return reports.report(p.getName(),from,to,channel);
    }
}
