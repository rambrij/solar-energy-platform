package com.saumya.pos.documents;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.checkout.PaymentCheckoutService;
import java.security.Principal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vendor/pos/receipts")
public class SaleReceiptController {
 private final PosService retail;private final PaymentCheckoutService payments;
 public SaleReceiptController(PosService retail,PaymentCheckoutService payments){this.retail=retail;this.payments=payments;}
 private Map<String,Object> read(String username,String id,boolean copy){var scope=retail.scope(username);var receipt=copy?retail.reprint(scope,id):retail.receipt(scope,id);receipt.put("tenders",payments.receiptTenders(username,id));return receipt;}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('POS_RECEIPT_VIEW')") public Object receipt(Principal actor,@PathVariable String id){return read(actor.getName(),id,false);}
 @PostMapping("/{id}/reprint") @PreAuthorize("hasAuthority('POS_RECEIPT_REPRINT')") public Object reprint(Principal actor,@PathVariable String id){return read(actor.getName(),id,true);}
}
