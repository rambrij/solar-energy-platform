package com.saumya.pos.marketplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class VendorSiteSurveyController {
    private final VendorSiteSurveyService service;
    public VendorSiteSurveyController(VendorSiteSurveyService service){this.service=service;}

    public record CreateRequest(
        @NotBlank @Size(max=500) String siteAddress,
        @NotNull Instant scheduledAt,
        @Size(max=40) String roofType,
        @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal monthlyConsumptionKwh,
        @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal proposedCapacityKw,
        @Size(max=2000) String notes,
        @NotBlank @Size(max=500) String reason) {}

    public record UpdateRequest(
        @Min(0) long expectedVersion,
        @NotBlank @Pattern(regexp="SCHEDULED|COMPLETED|CANCELLED") String status,
        Instant scheduledAt,
        @Size(max=40) String roofType,
        @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal monthlyConsumptionKwh,
        @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal proposedCapacityKw,
        @Size(max=4000) String findings,
        @NotBlank @Size(max=500) String reason) {}

    @GetMapping("/api/v1/vendor/site-surveys")
    @PreAuthorize("hasAuthority('SITE_SURVEY_VIEW')")
    public List<VendorSiteSurveyService.View> list(){return service.list();}

    @PostMapping("/api/v1/vendor/leads/{leadId}/site-surveys")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SITE_SURVEY_MANAGE')")
    public VendorSiteSurveyService.View create(@PathVariable long leadId,
            @Valid @RequestBody(required=false) CreateRequest request) {
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Site survey details are required");
        return service.create(leadId,new VendorSiteSurveyService.Create(request.siteAddress(),
            request.scheduledAt(),request.roofType(),request.monthlyConsumptionKwh(),
            request.proposedCapacityKw(),request.notes(),request.reason()));
    }

    @PatchMapping("/api/v1/vendor/site-surveys/{id}")
    @PreAuthorize("hasAuthority('SITE_SURVEY_MANAGE')")
    public VendorSiteSurveyService.View update(@PathVariable long id,
            @Valid @RequestBody(required=false) UpdateRequest request) {
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Site survey update is required");
        return service.update(id,new VendorSiteSurveyService.Update(request.expectedVersion(),
            request.status(),request.scheduledAt(),request.roofType(),
            request.monthlyConsumptionKwh(),request.proposedCapacityKw(),
            request.findings(),request.reason()));
    }
}
