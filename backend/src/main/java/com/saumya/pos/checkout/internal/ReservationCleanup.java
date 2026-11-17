package com.saumya.pos.checkout.internal;

import com.saumya.pos.checkout.DraftExpiryJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Compatibility facade for the former reservation sweep entry point.
 * DraftExpiryJob is the single scheduled owner and installs the dedicated system identity.
 */
@Component
@ConditionalOnProperty(name="checkout.cleanup-enabled", havingValue="true", matchIfMissing=true)
public class ReservationCleanup {
    private final DraftExpiryJob expiry;

    public ReservationCleanup(DraftExpiryJob expiry) {
        this.expiry = expiry;
    }

    /** Retained for any explicit internal invocation; never independently scheduled. */
    public void sweep() {
        expiry.scheduledCleanup();
    }
}
