package com.saumya.pos.documents;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
@RestController
public class ReceiptDocumentController {
 private final ReceiptDocumentService service;
 public ReceiptDocumentController(ReceiptDocumentService service){this.service=service;}
 public record Request(String reason){}
 @PostMapping("/api/vendor/pos/receipts/{id}/document") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RECEIPT_VIEW') and hasAuthority('POS_RECEIPT_REPRINT')")
 ResponseEntity<byte[]> generate(Principal principal,@PathVariable String id,@RequestBody Request request){var result=service.generate(principal.getName(),id,request.reason());return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.filename()).build().toString()).header("X-Document-Reference",result.reference()).header("X-Content-Type-Options","nosniff").contentLength(result.content().length).body(result.content());}
}
