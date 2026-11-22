package com.saumya.pos.membership;

import com.saumya.pos.security.PermissionCatalogue;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only implemented vendor operations from the canonical catalogue are assignable. */
public final class VendorPermissionRegistry {
    private static final Set<String> KNOWN = PermissionCatalogue.vendorPermissions();
    private VendorPermissionRegistry() { }

    public static Set<String> all() { return KNOWN; }

    public static String requireVendorPermission(String value) {
        String permission = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (permission.startsWith("PLATFORM_") || !KNOWN.contains(permission)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown, reserved, conflicting or non-vendor permission: " + permission);
        }
        return permission;
    }
}
