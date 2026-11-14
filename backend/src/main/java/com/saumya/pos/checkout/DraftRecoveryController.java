package com.saumya.pos.checkout;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class DraftRecoveryController {
 private final DraftRecoveryService recovery;
 public DraftRecoveryController(DraftRecoveryService recovery){this.recovery=recovery;}
 @GetMapping("/api/vendor/pos/drafts/{id}") @PreAuthorize("hasAuthority('POS_VIEW')")
 Object pos(Principal p,@PathVariable String id){return recovery.pos(p.getName(),id);}
 @GetMapping("/api/customer/checkout/drafts/{id}") @PreAuthorize("hasAuthority('CUSTOMER')")
 Object customer(Principal p,@PathVariable String id){return recovery.customer(p.getName(),id);}
}
