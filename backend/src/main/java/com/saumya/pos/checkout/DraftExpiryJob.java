package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.DraftOrder;
import com.saumya.pos.checkout.internal.DraftRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Trusted system boundary for expiring unpaid reservations. This worker is the only production
 * entry point that installs SYSTEM_DRAFT_EXPIRY; no HTTP endpoint exposes that capability.
 */
@Component
public class DraftExpiryJob {
    private static final String AUTHORITY = "SYSTEM_DRAFT_EXPIRY";
    private final DraftRepository orders;
    private final CheckoutService checkout;
    private final Clock clock;
    private final boolean enabled;

    public DraftExpiryJob(DraftRepository orders, CheckoutService checkout, Clock clock,
            @Value("${checkout.cleanup-enabled:true}") boolean enabled) {
        this.orders = orders;
        this.checkout = checkout;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${checkout.cleanup-delay-ms:60000}")
    public void scheduledCleanup() {
        if (enabled) runBatch();
    }

    /** One bounded batch keeps lock duration and database load predictable. */
    public int runBatch() {
        List<DraftOrder> due = orders.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                "DRAFT", clock.instant());
        int processed = 0;
        for (DraftOrder order : due) {
            try {
                asSystem(() -> checkout.expire(order.tenant(), order.id()));
                processed++;
            } catch (ResponseStatusException denied) {
                // A tenant may have been suspended after the due row was selected.
                // Skip that row without blocking expiry work for other tenants.
                if (denied.getStatusCode().value() != 403) throw denied;
            }
        }
        return processed;
    }

    private void asSystem(Runnable action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext system = SecurityContextHolder.createEmptyContext();
        system.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "system:draft-expiry", null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        SecurityContextHolder.setContext(system);
        try {
            action.run();
        } finally {
            // Scheduler threads are pooled. Never leave a worker identity on a reused thread.
            if (previous.getAuthentication() == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.setContext(previous);
            }
        }
    }
}
