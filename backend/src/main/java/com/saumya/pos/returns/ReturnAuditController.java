package com.saumya.pos.returns;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class ReturnAuditController {
 private final ReturnAuditService service;
 public ReturnAuditController(ReturnAuditService service){this.service=service;}
 @GetMapping("/api/vendor/pos/returns/{id}/events") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_AUDIT_VIEW')")
 Object history(Principal p,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return service.history(p.getName(),id,page,size);}
}
