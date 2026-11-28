package com.saumya.pos.checkout;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only invoice register endpoint; tenant scope is derived from the active vendor actor. */
@RestController
@RequestMapping("/api/v1/vendor/invoices")
public class VendorInvoiceController {
    private final VendorInvoiceRegisterService service;

    public VendorInvoiceController(VendorInvoiceRegisterService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_VIEW')")
    public VendorInvoiceRegisterService.Page list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String channel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(from, to, channel, page, size);
    }
}
