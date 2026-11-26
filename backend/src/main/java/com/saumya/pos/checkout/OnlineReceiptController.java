package com.saumya.pos.checkout;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.saumya.pos.documents.ReceiptPdfRenderer;
@RestController @RequestMapping("/api/v1/customer/orders/{id}") @PreAuthorize("hasAuthority('CUSTOMER')")
public class OnlineReceiptController {
 private final OnlineReceiptService receipts;private final ReceiptPdfRenderer pdf;
 public OnlineReceiptController(OnlineReceiptService receipts,ReceiptPdfRenderer pdf){this.receipts=receipts;this.pdf=pdf;}
 @GetMapping("/receipt") Object read(Principal p,@PathVariable String id){return receipts.read(p.getName(),id);}
 public record CopyRequest(){}
 @PostMapping("/receipt-copy") Object copy(Principal p,@PathVariable String id,@RequestBody CopyRequest request){return receipts.copy(p.getName(),id);}

 @PostMapping("/receipt-document")
 ResponseEntity<byte[]> downloadPdfCopy(Principal p,@PathVariable String id){
  var saved=receipts.copy(p.getName(),id);
  String reference=UUID.randomUUID().toString();
  byte[] content=pdf.renderCustomerCopy(saved,reference,Instant.now());
  if(content.length<5||content.length>2_000_000)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt PDF exceeds the 2 MB document limit");
  String filename="receipt-"+saved.orderId()+"-customer-copy-"+reference+".pdf";
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString())
   .header("X-Document-Reference",reference).header("X-Content-Type-Options","nosniff")
   .contentLength(content.length).body(content);
 }
}
