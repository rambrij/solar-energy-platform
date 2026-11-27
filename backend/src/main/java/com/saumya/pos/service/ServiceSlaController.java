package com.saumya.pos.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ServiceSlaController {
    private final ServiceSlaService sla;

    public ServiceSlaController(ServiceSlaService sla) {
        this.sla = sla;
    }

    public record PolicyRequest(@NotBlank @Size(max=24) String caseType,
            @NotBlank @Size(max=16) String priority,
            int responseTargetMinutes, int resolutionTargetMinutes,
            @NotBlank @Size(max=500) String reason) {}

    @GetMapping("/api/v1/vendor/service-sla")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<ServiceSlaService.CaseSla> dashboard() {
        return sla.dashboard();
    }

    @GetMapping("/api/v1/vendor/service-sla/policies")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<ServiceSlaService.Policy> policies() {
        return sla.policies();
    }

    @PutMapping("/api/v1/vendor/service-sla/policies")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public ServiceSlaService.Policy updatePolicy(@Valid @RequestBody PolicyRequest request) {
        return sla.updatePolicy(new ServiceSlaService.PolicyUpdate(request.caseType(), request.priority(),
            request.responseTargetMinutes(), request.resolutionTargetMinutes(), request.reason()));
    }
}
