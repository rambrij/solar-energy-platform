package com.saumya.pos.marketplace;

import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuotationController {
    private final QuotationService quotations;
    public QuotationController(QuotationService quotations) { this.quotations=quotations; }

    public record CreateRequest(@Positive long enquiryId, @NotEmpty @Size(max=100) java.util.List<@Valid LineRequest> items,
            @Min(1) @Max(90) int validityDays, @Size(max=4000) String terms, @NotBlank @Size(max=500) String reason) {}
    public record ReviseRequest(@NotEmpty @Size(max=100) java.util.List<@Valid LineRequest> items,
            @Min(1) @Max(90) int validityDays, @Size(max=4000) String terms, @NotBlank @Size(max=500) String reason) {}
    public record LineRequest(@Positive long productId, @Min(1) @Max(10000) int quantity) {}
    public record DecisionRequest(@NotNull Boolean accept, @Size(max=500) String reason) {}

    @GetMapping("/api/v1/vendor/quotations")
    @PreAuthorize("hasAuthority('LEAD_VIEW')")
    Object vendorList() { return quotations.vendorList(); }

    @PostMapping("/api/v1/vendor/quotations")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    Object create(@Valid @RequestBody CreateRequest request) {
        return quotations.create(new QuotationService.Create(request.enquiryId(),
            request.items().stream().map(line->new QuotationService.LineInput(line.productId(),line.quantity())).toList(),
            request.validityDays(),request.terms(),request.reason()));
    }

    @PostMapping("/api/v1/vendor/quotations/{id}/revisions")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    Object revise(@PathVariable long id,@Valid @RequestBody ReviseRequest request) {
        return quotations.revise(id,new QuotationService.Revise(
            request.items().stream().map(line->new QuotationService.LineInput(line.productId(),line.quantity())).toList(),
            request.validityDays(),request.terms(),request.reason()));
    }

    @GetMapping("/api/v1/customer/quotations")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    Object customerList(Principal principal) { return quotations.customerList(principal.getName()); }

    @GetMapping("/api/v1/customer/quotations/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    Object customerGet(Principal principal,@PathVariable long id) {
        return quotations.customerGet(principal.getName(),id);
    }

    @PostMapping("/api/v1/customer/quotations/{id}/revisions/{revision}/decision")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    Object decide(Principal principal,@PathVariable long id,@PathVariable int revision,
            @Valid @RequestBody DecisionRequest request) {
        return quotations.decide(principal.getName(),id,revision,request.accept(),request.reason());
    }
}
