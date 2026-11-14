package com.saumya.pos.checkout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Simulation is deliberately restricted to explicitly selected local/test environments. */
@Component
public class PaymentPolicy {
    private final boolean enabled;
    public PaymentPolicy(@Value("${payments.mock-enabled:false}") boolean enabled, Environment environment) {
        var profiles=Set.of(environment.getActiveProfiles());
        if(enabled && (Collections.disjoint(profiles,Set.of("local","test")) || profiles.contains("prod") || profiles.contains("production")))
            throw new IllegalStateException("Mock payments require local or test profile and are forbidden in production");
        this.enabled=enabled;
    }
    public boolean enabled(){return enabled;}
    public void requireEnabled(){if(!enabled)throw new ResponseStatusException(CONFLICT,"Vendor payment adapter is not configured");}
    public void method(String method){if(method==null||!Set.of("UPI","CARD").contains(method))throw new ResponseStatusException(BAD_REQUEST,"Choose UPI or CARD");}
}
