package com.saumya.pos.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ServiceVisitController {
    private final ServiceVisitService visits;
    public ServiceVisitController(ServiceVisitService visits){this.visits=visits;}

    public record CreateRequest(@NotBlank @Size(max=24) String caseType,@Positive long caseId,
        @NotBlank @Size(max=1000) String siteAddress,@NotNull Instant scheduledAt,
        @NotBlank @Size(max=100) String assignedTo,@NotBlank @Size(max=500) String reason){}
    public record AssignmentRequest(long expectedVersion,@NotBlank @Size(max=100) String assignedTo,
        @NotNull Instant scheduledAt,@NotBlank @Size(max=500) String reason){}
    public record StatusRequest(long expectedVersion,@NotBlank @Size(max=20) String status,
        @NotBlank @Size(max=1000) String note){}
    public record NoteRequest(long expectedVersion,@NotBlank @Size(max=1000) String note,
        boolean customerVisible){}

    @GetMapping("/api/v1/vendor/service-visits")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<ServiceVisitService.VendorVisit> vendorList(){return visits.vendorList();}

    @GetMapping("/api/v1/vendor/service-visits/assignees")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public List<String> eligibleAssignees(){return visits.eligibleAssignees();}

    @GetMapping("/api/v1/vendor/service-visits/{id}")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public ServiceVisitService.VendorDetail vendorGet(@PathVariable long id){return visits.vendorGet(id);}

    @PostMapping("/api/v1/vendor/service-visits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public ServiceVisitService.VendorVisit create(@Valid @RequestBody CreateRequest request){
        return visits.create(new ServiceVisitService.Create(request.caseType(),request.caseId(),
            request.siteAddress(),request.scheduledAt(),request.assignedTo(),request.reason()));
    }

    @PatchMapping("/api/v1/vendor/service-visits/{id}/assignment")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public ServiceVisitService.VendorVisit assign(@PathVariable long id,
            @Valid @RequestBody AssignmentRequest request){
        return visits.assign(id,new ServiceVisitService.Assignment(request.expectedVersion(),
            request.assignedTo(),request.scheduledAt(),request.reason()));
    }

    @PatchMapping("/api/v1/vendor/service-visits/{id}/status")
    @PreAuthorize("hasAnyAuthority('SERVICE_UPDATE','SERVICE_RESOLVE','SERVICE_ASSIGN')")
    public ServiceVisitService.VendorVisit updateStatus(@PathVariable long id,
            @Valid @RequestBody StatusRequest request){
        return visits.updateStatus(id,new ServiceVisitService.StatusChange(
            request.expectedVersion(),request.status(),request.note()));
    }

    @PostMapping("/api/v1/vendor/service-visits/{id}/notes")
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    public ServiceVisitService.VendorVisit recordNote(@PathVariable long id,
            @Valid @RequestBody NoteRequest request){
        return visits.recordNote(id,new ServiceVisitService.VisitNote(
            request.expectedVersion(),request.note(),request.customerVisible()));
    }

    @GetMapping("/api/v1/customer/service-visits")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public List<ServiceVisitService.CustomerVisit> customerList(Principal principal){
        return visits.customerList(principal.getName());
    }

    @GetMapping("/api/v1/customer/service-visits/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ServiceVisitService.CustomerDetail customerGet(Principal principal,@PathVariable long id){
        return visits.customerGet(principal.getName(),id);
    }
}
