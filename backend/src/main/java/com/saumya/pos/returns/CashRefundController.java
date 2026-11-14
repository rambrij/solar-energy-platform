package com.saumya.pos.returns;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/vendor/pos/refunds")
@PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW')")
public class CashRefundController {
 private final CashRefundService service;
 public CashRefundController(CashRefundService service){this.service=service;}
 @GetMapping Object list(Principal p){return service.list(p.getName());}
 @GetMapping("/{id}") Object get(Principal p,@PathVariable String id){return service.get(p.getName(),id);}
 @GetMapping("/return/{id}") Object byReturn(Principal p,@PathVariable String id){return service.byReturn(p.getName(),id);}
 @GetMapping("/quote/{returnId}") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW') and hasAuthority('POS_REFUND_APPROVE') and hasAuthority('POS_REFUND_CASH_RECORD')")
 Object quote(Principal p,@PathVariable String returnId,@RequestParam String sessionId){return service.quote(p.getName(),returnId,sessionId);}
 @PostMapping @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW') and hasAuthority('POS_REFUND_APPROVE') and hasAuthority('POS_REFUND_CASH_RECORD')")
 Object pay(Principal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CashRefundService.Pay request){return service.pay(p.getName(),key,request);}
 @PostMapping("/{id}/copy") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_REFUND_VIEW') and hasAuthority('POS_REFUND_PRINT')")
 Object copy(Principal p,@PathVariable String id){return service.copy(p.getName(),id);}
}
