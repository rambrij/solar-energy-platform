package com.saumya.pos.installation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class InstallationController {
    private final InstallationService installations;
    public InstallationController(InstallationService installations){this.installations=installations;}

    public record CreateRequest(@NotBlank @Size(max=36) String orderId,
        @Size(max=1000) String siteAddress,@NotNull Instant scheduledAt,
        @NotBlank @Size(max=100) String assignedTo,@NotBlank @Size(max=500) String reason) {}
    public record AssignRequest(long expectedVersion,@NotBlank @Size(max=100) String assignedTo,
        @NotNull Instant scheduledAt,@NotBlank @Size(max=500) String reason) {}
    public record StatusRequest(long expectedVersion,
        @NotBlank @Size(max=20) String status,@NotBlank @Size(max=1000) String note) {}
    public record ChecklistRequest(long expectedVersion,
        @NotBlank @Size(max=1000) String evidenceNote) {}

    @GetMapping("/api/v1/vendor/installations/orders")
    @PreAuthorize("hasAuthority('INSTALLATION_ASSIGN')")
    public List<InstallationService.EligibleOrder> eligibleOrders(){return installations.eligibleOrders();}

    @GetMapping("/api/v1/vendor/installations/engineers")
    @PreAuthorize("hasAuthority('INSTALLATION_ASSIGN')")
    public List<String> eligibleEngineers(){return installations.eligibleEngineers();}

    @GetMapping("/api/v1/vendor/installations")
    @PreAuthorize("hasAuthority('INSTALLATION_VIEW')")
    public List<InstallationService.Job> vendorList(){return installations.vendorList();}

    @GetMapping("/api/v1/vendor/installations/{id}")
    @PreAuthorize("hasAuthority('INSTALLATION_VIEW')")
    public InstallationService.Detail vendorGet(@PathVariable long id){return installations.vendorGet(id);}

    @PostMapping("/api/v1/vendor/installations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INSTALLATION_ASSIGN')")
    public InstallationService.Job create(@RequestBody(required=false) CreateRequest request){
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Installation details are required");
        return installations.create(new InstallationService.Create(request.orderId(),request.siteAddress(),
            request.scheduledAt(),request.assignedTo(),request.reason()));
    }

    @PatchMapping("/api/v1/vendor/installations/{id}/assignment")
    @PreAuthorize("hasAuthority('INSTALLATION_ASSIGN')")
    public InstallationService.Job assign(@PathVariable long id,
            @RequestBody(required=false) AssignRequest request){
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Installation assignment is required");
        return installations.assign(id,new InstallationService.Assign(request.expectedVersion(),
            request.assignedTo(),request.scheduledAt(),request.reason()));
    }

    @PatchMapping("/api/v1/vendor/installations/{id}/status")
    @PreAuthorize("hasAuthority('INSTALLATION_UPDATE')")
    public InstallationService.Job updateStatus(@PathVariable long id,
            @RequestBody(required=false) StatusRequest request){
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Installation status update is required");
        return installations.updateStatus(id,new InstallationService.StatusChange(
            request.expectedVersion(),request.status(),request.note()));
    }

    @PatchMapping("/api/v1/vendor/installations/{id}/checklist/{itemKey}")
    @PreAuthorize("hasAuthority('INSTALLATION_UPDATE')")
    public InstallationService.Detail completeChecklistItem(@PathVariable long id,@PathVariable String itemKey,
            @Valid @RequestBody(required=false) ChecklistRequest request) {
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Checklist evidence is required");
        return installations.completeChecklistItem(id,itemKey,
            new InstallationService.ChecklistCompletion(request.expectedVersion(),request.evidenceNote()));
    }

    @GetMapping("/api/v1/customer/installations")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public List<InstallationService.CustomerJob> customerList(Principal principal){
        return installations.customerList(principal.getName());
    }

    @GetMapping("/api/v1/customer/installations/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public InstallationService.CustomerDetail customerGet(Principal principal,@PathVariable long id){
        return installations.customerGet(principal.getName(),id);
    }
}
