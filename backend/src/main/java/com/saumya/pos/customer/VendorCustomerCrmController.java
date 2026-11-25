package com.saumya.pos.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor/customers")
public class VendorCustomerCrmController {
    private final VendorCustomerCrmService service;

    public VendorCustomerCrmController(VendorCustomerCrmService service) { this.service = service; }

    public record NoteCreate(@NotBlank @Size(max = 2000) String note,
                             @NotBlank @Size(max = 500) String reason) {}
    public record NoteUpdate(@PositiveOrZero long expectedVersion,
                             @NotBlank @Size(max = 2000) String note,
                             @NotBlank @Size(max = 500) String reason) {}

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_RELATION_VIEW')")
    public List<VendorCustomerCrmService.CustomerRelationship> customers() { return service.customers(); }

    @GetMapping("/{customerId}/crm-notes")
    @PreAuthorize("hasAuthority('CRM_NOTE_VIEW')")
    public List<VendorCustomerCrmService.CrmNote> notes(@PathVariable long customerId) {
        return service.notes(customerId);
    }

    @PostMapping("/{customerId}/crm-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CRM_NOTE_CREATE')")
    public VendorCustomerCrmService.CrmNote createNote(
        @PathVariable long customerId, @Valid @RequestBody NoteCreate request) {
        return service.createNote(customerId, request.note(), request.reason());
    }

    @PutMapping("/{customerId}/crm-notes/{noteId}")
    @PreAuthorize("hasAuthority('CRM_NOTE_UPDATE')")
    public VendorCustomerCrmService.CrmNote updateNote(
        @PathVariable long customerId, @PathVariable long noteId, @Valid @RequestBody NoteUpdate request) {
        return service.updateNote(customerId, noteId, request.expectedVersion(), request.note(), request.reason());
    }
}
