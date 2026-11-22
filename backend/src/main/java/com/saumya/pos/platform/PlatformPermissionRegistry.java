package com.saumya.pos.platform;

import com.saumya.pos.security.PermissionCatalogue;
import java.util.Set;

/** Runtime allow-list for platform-only grants, sourced from the canonical catalogue. */
public final class PlatformPermissionRegistry {
    private PlatformPermissionRegistry() { }
    private static final Set<String> PERMISSIONS = PermissionCatalogue.platformPermissions();
    public static Set<String> all() { return PERMISSIONS; }
    public static boolean contains(String permission) { return PERMISSIONS.contains(permission); }
}
