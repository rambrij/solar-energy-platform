package com.saumya.pos.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor/profile")
public class VendorTenantProfileController {
    private final VendorTenantProfileService service;
    public VendorTenantProfileController(VendorTenantProfileService service){this.service=service;}
    public record ProfileUpdateRequest(@PositiveOrZero long expectedVersion,@NotBlank @Size(max=160) String name,@NotBlank @Size(max=180) String legalName,@Email @Size(max=100) String contactEmail,@Size(max=30) String contactPhone,@Size(max=40) String taxIdentifier,@Size(max=500) String address) {}
    @GetMapping @PreAuthorize("hasAuthority('TENANT_PROFILE_VIEW')")
    public VendorTenantProfileService.Profile read(){return service.read();}
    @PutMapping @PreAuthorize("hasAuthority('TENANT_PROFILE_UPDATE')")
    public VendorTenantProfileService.Profile update(@Valid @RequestBody ProfileUpdateRequest request) {
        return service.update(new VendorTenantProfileService.Update(request.expectedVersion(),request.name(),request.legalName(),request.contactEmail(),request.contactPhone(),request.taxIdentifier(),request.address()));
    }
}
