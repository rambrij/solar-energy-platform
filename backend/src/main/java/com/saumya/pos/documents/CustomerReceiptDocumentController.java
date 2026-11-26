package com.saumya.pos.documents;

import java.security.Principal;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/orders/{id}")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class CustomerReceiptDocumentController {
    private final CustomerReceiptDocumentService documents;

    public CustomerReceiptDocumentController(CustomerReceiptDocumentService documents) {
        this.documents = documents;
    }

    @PostMapping("/receipt-document")
    public ResponseEntity<byte[]> downloadPdfCopy(Principal principal, @PathVariable String id) {
        var result = documents.generate(principal.getName(), id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(result.filename()).build().toString())
            .header("X-Document-Reference", result.reference())
            .header("X-Content-Type-Options", "nosniff")
            .contentLength(result.content().length)
            .body(result.content());
    }
}
