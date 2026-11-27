package com.saumya.pos.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ServiceRequestController {
    private final ServiceRequestService requests;

    public ServiceRequestController(ServiceRequestService requests) {
        this.requests = requests;
    }

    public record CreateRequest(
        @NotBlank @Size(max=36) String orderId,
        @NotBlank @Size(max=160) String subject,
        @NotBlank @Size(max=2000) String description,
        @Size(max=16) String priority
    ) {}

    public record AssignRequest(
        long expectedVersion,
        @NotBlank @Size(max=100) String assignedTo,
        @NotBlank @Size(max=500) String reason
    ) {}

    public record StatusRequest(
        long expectedVersion,
        @NotBlank @Size(max=24) String status,
        @NotBlank @Size(max=1000) String note,
        boolean customerVisible
    ) {}

    @GetMapping("/api/v1/vendor/service-requests/orders")
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    public List<ServiceRequestService.EligibleOrder> eligibleVendorOrders() {
        return requests.eligibleVendorOrders();
    }

    @GetMapping("/api/v1/vendor/service-requests/assignees")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public List<String> eligibleAssignees() {
        return requests.eligibleAssignees();
    }

    @GetMapping("/api/v1/vendor/service-requests")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<ServiceRequestService.VendorRequest> vendorList() {
        return requests.vendorList();
    }

    @GetMapping("/api/v1/vendor/service-requests/{id}")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public ServiceRequestService.VendorDetail vendorGet(@PathVariable long id) {
        return requests.vendorGet(id);
    }

    @PostMapping("/api/v1/vendor/service-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    public ServiceRequestService.VendorRequest vendorCreate(@Valid @RequestBody(required=false) CreateRequest request) {
        if (request == null) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Service request details are required");
        return requests.createForVendor(new ServiceRequestService.Create(
            request.orderId(), request.subject(), request.description(), request.priority()));
    }

    @PatchMapping("/api/v1/vendor/service-requests/{id}/assignment")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public ServiceRequestService.VendorRequest assign(
            @PathVariable long id, @Valid @RequestBody(required=false) AssignRequest request) {
        if (request == null) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Service assignment is required");
        return requests.assign(id, new ServiceRequestService.Assign(
            request.expectedVersion(), request.assignedTo(), request.reason()));
    }

    @PatchMapping("/api/v1/vendor/service-requests/{id}/status")
    @PreAuthorize("hasAnyAuthority('SERVICE_UPDATE','SERVICE_RESOLVE')")
    public ServiceRequestService.VendorRequest updateStatus(
            @PathVariable long id, @Valid @RequestBody(required=false) StatusRequest request) {
        if (request == null) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Service status update is required");
        return requests.updateStatus(id, new ServiceRequestService.StatusChange(
            request.expectedVersion(), request.status(), request.note(), request.customerVisible()));
    }

    @GetMapping("/api/v1/customer/service-requests/orders")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public List<ServiceRequestService.EligibleOrder> eligibleCustomerOrders(Principal principal) {
        return requests.eligibleCustomerOrders(principal.getName());
    }

    @GetMapping("/api/v1/customer/service-requests")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public List<ServiceRequestService.CustomerRequest> customerList(Principal principal) {
        return requests.customerList(principal.getName());
    }

    @GetMapping("/api/v1/customer/service-requests/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ServiceRequestService.CustomerDetail customerGet(Principal principal, @PathVariable long id) {
        return requests.customerGet(principal.getName(), id);
    }

    @PostMapping("/api/v1/customer/service-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ServiceRequestService.CustomerRequest customerCreate(
            Principal principal, @Valid @RequestBody(required=false) CreateRequest request) {
        if (request == null) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Service request details are required");
        return requests.createForCustomer(principal.getName(), new ServiceRequestService.Create(
            request.orderId(), request.subject(), request.description(), request.priority()));
    }
}
