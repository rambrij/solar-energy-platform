package com.saumya.pos.service;

import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs bounded tenant-scoped SLA breach detection for active vendor tenants. */
@Component
public class ServiceSlaEscalationWorker {
    private static final Logger log = LoggerFactory.getLogger(ServiceSlaEscalationWorker.class);

    private final TenantLifecycleService tenants;
    private final ServiceSlaService sla;
    private final ServiceSlaEscalationService escalations;
    private final Clock clock;

    public ServiceSlaEscalationWorker(TenantLifecycleService tenants, ServiceSlaService sla,
            ServiceSlaEscalationService escalations, Clock clock) {
        this.tenants = tenants;
        this.sla = sla;
        this.escalations = escalations;
        this.clock = clock;
    }

    @Scheduled(
        fixedDelayString = "$" + "{service.sla.escalation.poll-delay-ms:60000}",
        initialDelayString = "$" + "{service.sla.escalation.initial-delay-ms:30000}")
    public void scheduledEscalation() {
        var now = clock.instant();
        for (var tenant : tenants.activeVendors()) {
            try {
                sla.processDueForTenant(tenant, now);
                escalations.processDueEscalationsForTenant(tenant, now);
            } catch (RuntimeException failure) {
                log.error("Service SLA escalation failed for tenant {} ({})",
                    tenant.id(), failure.getClass().getSimpleName());
            }
        }
    }
}
