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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentDisposition(ContentDisposition.attachment().filename(result.filename()).build());
        headers.set("X-Document-Reference", result.reference());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setAccessControlExposeHeaders(java.util.List.of("X-Document-Reference", "Content-Disposition"));
        headers.setContentLength(result.content().length);
        return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);
    }
}
