package com.saumya.pos.marketplace;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class VendorLeadController {
    private final MarketplaceEnquiryService service;
    public VendorLeadController(MarketplaceEnquiryService service){this.service=service;}

    @GetMapping("/api/v1/vendor/leads")
    @PreAuthorize("hasAuthority('LEAD_VIEW')")
    public List<MarketplaceEnquiryService.Lead> list(){return service.leads();}
}
