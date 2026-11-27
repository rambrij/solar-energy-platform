package com.saumya.pos.warranty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class WarrantyController {
    private final WarrantyService warranties;
    public WarrantyController(WarrantyService warranties){this.warranties=warranties;}

    public record RegisterRequest(@NotBlank @Size(max=36) String orderId,
        @Positive long productId,@NotNull LocalDate coverageStartDate,@NotNull LocalDate coverageEndDate,
        @NotBlank @Size(max=2000) String termsSummary,@NotBlank @Size(max=500) String reason){}

    @GetMapping("/api/v1/vendor/warranties/orders")
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    public List<WarrantyService.EligibleOrder> eligibleOrders(){return warranties.eligibleOrders();}

    @GetMapping("/api/v1/vendor/warranties/orders/{orderId}/items")
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    public List<WarrantyService.EligibleItem> eligibleItems(@PathVariable String orderId){
        return warranties.eligibleItems(orderId);
    }

    @GetMapping("/api/v1/vendor/warranties")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<WarrantyService.Warranty> vendorList(){return warranties.vendorList();}

    @PostMapping("/api/v1/vendor/warranties")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    public WarrantyService.Warranty register(@Valid @RequestBody(required=false) RegisterRequest request){
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Warranty registration details are required");
        return warranties.register(new WarrantyService.Register(request.orderId(),request.productId(),
            request.coverageStartDate(),request.coverageEndDate(),request.termsSummary(),request.reason()));
    }

    @GetMapping("/api/v1/customer/warranties")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public List<WarrantyService.CustomerWarranty> customerList(Principal principal){
        return warranties.customerList(principal.getName());
    }

    @GetMapping("/api/v1/customer/warranties/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public WarrantyService.CustomerWarranty customerGet(Principal principal,@PathVariable long id){
        return warranties.customerGet(principal.getName(),id);
    }
}
