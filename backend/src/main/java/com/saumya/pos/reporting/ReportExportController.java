package com.saumya.pos.reporting;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
@RestController @RequestMapping("/api/vendor/pos/reports")
public class ReportExportController {
 private final ReportExportService service;
 public ReportExportController(ReportExportService service){this.service=service;}
 public record Sales(LocalDate from,LocalDate to,String channel,String reason){}
 public record Refunds(LocalDate from,LocalDate to,String reason){}
 @PostMapping("/sales/export") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REPORT_VIEW') and hasAuthority('POS_REPORT_EXPORT')")
 ResponseEntity<byte[]> sales(Principal p,@RequestBody Sales request){return response(service.sales(p.getName(),request.from(),request.to(),request.channel(),request.reason()));}
 @PostMapping("/cash-refunds/export") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REPORT_VIEW') and hasAuthority('POS_REPORT_EXPORT') and hasAuthority('POS_REFUND_VIEW')")
 ResponseEntity<byte[]> refunds(Principal p,@RequestBody Refunds request){return response(service.refunds(p.getName(),request.from(),request.to(),request.reason()));}
 private ResponseEntity<byte[]> response(ReportExportService.Export result){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.filename()).build().toString()).header("X-Export-Reference",result.id()).header("X-Export-Row-Count",Integer.toString(result.rows())).contentLength(result.content().length).body(result.content());}
}
