package com.saumya.pos.shared;

/** Port used during tenant onboarding to obtain the tenant's first outlet without the platform module depending on retail. */
public interface OutletProvisioner {
    /** Returns the tenant's first outlet id, creating a default outlet when none exists. Idempotent per tenant. */
    long defaultOutlet(long tenant,String name);
}
