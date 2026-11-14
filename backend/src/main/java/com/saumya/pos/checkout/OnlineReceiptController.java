package com.saumya.pos.checkout;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/customer/orders/{id}") @PreAuthorize("hasAuthority('CUSTOMER')")
public class OnlineReceiptController {
 private final OnlineReceiptService receipts;
 public OnlineReceiptController(OnlineReceiptService receipts){this.receipts=receipts;}
 @GetMapping("/receipt") Object read(Principal p,@PathVariable String id){return receipts.read(p.getName(),id);}
 public record CopyRequest(){}
 @PostMapping("/receipt-copy") Object copy(Principal p,@PathVariable String id,@RequestBody CopyRequest request){return receipts.copy(p.getName(),id);}
}
