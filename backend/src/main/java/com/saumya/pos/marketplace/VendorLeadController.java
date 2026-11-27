package com.saumya.pos.marketplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class VendorLeadController {
    private final MarketplaceEnquiryService service;
    public VendorLeadController(MarketplaceEnquiryService service){this.service=service;}

    public record StatusRequest(
        @NotBlank @Pattern(regexp="NEW|CONTACTED|QUALIFIED|LOST") String status,
        @Min(0) long expectedVersion,
        @NotBlank @Size(max=500) String reason) {}
    public record AssignmentRequest(
        @Size(max=100) String employeeUsername,
        @Min(0) long expectedVersion,
        @NotBlank @Size(max=500) String reason) {}

    @GetMapping("/api/v1/vendor/leads")
    @PreAuthorize("hasAuthority('LEAD_VIEW')")
    public List<MarketplaceEnquiryService.Lead> list(){return service.leads();}

    @GetMapping("/api/v1/vendor/leads/assignees")
    @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
    public List<String> assignees(){return service.leadAssignees();}

    @PatchMapping("/api/v1/vendor/leads/{id}")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    public MarketplaceEnquiryService.Lead updateStatus(@PathVariable long id,
            @Valid @RequestBody(required=false) StatusRequest request) {
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Lead status and change reason are required");
        return service.updateLeadStatus(id,request.status(),request.expectedVersion(),request.reason());
    }

    @PutMapping("/api/v1/vendor/leads/{id}/assignment")
    @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
    public MarketplaceEnquiryService.Lead assign(@PathVariable long id,
            @Valid @RequestBody(required=false) AssignmentRequest request) {
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Lead assignee and change reason are required");
        return service.assignLead(id,request.employeeUsername(),request.expectedVersion(),request.reason());
    }
}
