package com.saumya.pos.returns;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/vendor/pos/returns")
@PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW')")
public class ReturnController {
 private final ReturnService service;
 public ReturnController(ReturnService service){this.service=service;}
 @GetMapping Object list(Principal p,@RequestParam(defaultValue="REQUESTED") String state){return service.list(p.getName(),state);}
 @GetMapping("/{id}") Object get(Principal p,@PathVariable String id){return service.get(p.getName(),id);}
 @GetMapping("/sale/{orderId}") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_CREATE')")
 Object eligibility(Principal p,@PathVariable String orderId){return service.eligibility(p.getName(),orderId);}
 @PostMapping @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_CREATE')")
 Object create(Principal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ReturnService.Create r){return service.create(p.getName(),key,r);}
 @PostMapping("/{id}/review") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_APPROVE')")
 Object review(Principal p,@PathVariable String id,@Valid @RequestBody ReturnService.Review r){return service.review(p.getName(),id,r);}
 @PostMapping("/{id}/inspect") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_INSPECT')")
 Object inspect(Principal p,@PathVariable String id,@Valid @RequestBody ReturnService.Inspect r){return service.inspect(p.getName(),id,r);}
}
