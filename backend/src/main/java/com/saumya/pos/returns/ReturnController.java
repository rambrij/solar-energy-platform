package com.saumya.pos.returns;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/vendor/pos/returns")
@PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW')")
public class ReturnController {
    private final ReturnService service;
    
    public ReturnController(ReturnService service) {
        this.service = service;
    }
    
    @GetMapping
    Object list(@RequestParam(defaultValue = "REQUESTED") String state) {
        return service.list(state);
    }
    
    @GetMapping("/{id}")
    Object get(@PathVariable String id) {
        return service.get(id);
    }
    
    @GetMapping("/sale/{orderId}")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_CREATE')")
    Object eligibility(@PathVariable String orderId) {
        return service.eligibility(orderId);
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_CREATE')")
    Object create(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ReturnService.Create r) {
        return service.create(key, r);
    }
    
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_APPROVE')")
    Object review(@PathVariable String id, @Valid @RequestBody ReturnService.Review r) {
        return service.review(id, r);
    }
    
    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_INSPECT')")
    Object inspect(@PathVariable String id, @Valid @RequestBody ReturnService.Inspect r) {
        return service.inspect(id, r);
    }
}
