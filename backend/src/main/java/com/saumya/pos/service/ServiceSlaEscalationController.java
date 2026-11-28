package com.saumya.pos.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor/service-sla")
public class ServiceSlaEscalationController {
    private final ServiceSlaEscalationService escalations;
    public ServiceSlaEscalationController(ServiceSlaEscalationService escalations) {
        this.escalations = escalations;
    }
    public record PolicyRequest(@NotBlank @Size(max=24) String caseType,
        @NotBlank @Size(max=16) String priority, @NotBlank @Size(max=16) String breachType,
        @Min(1) @Max(5) int escalationLevel, @Min(0) @Max(525600) int afterMinutes,
        @NotBlank @Size(max=64) String recipientRoleKey, boolean enabled,
        @NotBlank @Size(max=500) String reason) {}
    public record AcknowledgeRequest(@NotBlank @Size(max=500) String note) {}

    @GetMapping("/escalation-policies")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<ServiceSlaEscalationService.Policy> policies() { return escalations.policies(); }

    @PutMapping("/escalation-policies")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public ServiceSlaEscalationService.Policy savePolicy(@Valid @RequestBody PolicyRequest request) {
        return escalations.savePolicy(new ServiceSlaEscalationService.PolicyUpdate(
            request.caseType(), request.priority(), request.breachType(), request.escalationLevel(),
            request.afterMinutes(), request.recipientRoleKey(), request.enabled(), request.reason()));
    }

    @GetMapping("/escalations")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<ServiceSlaEscalationService.Escalation> queue() { return escalations.queue(); }

    @PostMapping("/escalations/{id}/acknowledge")
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    public ServiceSlaEscalationService.Escalation acknowledge(@PathVariable long id,
            @Valid @RequestBody AcknowledgeRequest request) {
        return escalations.acknowledge(id, new ServiceSlaEscalationService.Acknowledge(request.note()));
    }
}
