package com.saumya.pos.checkout;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class CashierSalesController {
 private final CashierSalesService service;
 public CashierSalesController(CashierSalesService service){this.service=service;}
 @GetMapping("/api/vendor/pos/reports/cashier-sales") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REPORT_VIEW') and hasAuthority('POS_CASHIER_REPORT_VIEW')")
 Object report(Principal p,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return service.report(p.getName(),from,to,page,size);}
}
