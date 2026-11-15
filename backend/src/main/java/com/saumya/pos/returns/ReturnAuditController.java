package com.saumya.pos.returns;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class ReturnAuditController {
    private final ReturnAuditService service;
    
    public ReturnAuditController(ReturnAuditService service) {
        this.service = service;
    }
    
    @GetMapping("/api/v1/vendor/pos/returns/{id}/events")
    @PreAuthorize(
            "hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW') and hasAuthority('POS_RETURN_AUDIT_VIEW')"
    )
    Object history(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return service.history(id, page, size);
    }
}
