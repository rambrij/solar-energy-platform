package com.saumya.pos.platform;

import java.util.Set;

/** Runtime allow-list for persisted platform grants. */
public final class PlatformPermissionRegistry {
    private PlatformPermissionRegistry(){}
    private static final Set<String> PERMISSIONS=Set.of(
        "PLATFORM_TENANT_VIEW","PLATFORM_TENANT_CREATE","PLATFORM_TENANT_UPDATE","PLATFORM_TENANT_REACTIVATE",
        "PLATFORM_TENANT_SUSPEND","PLATFORM_TENANT_CLOSE","PLATFORM_PLAN_MANAGE","PLATFORM_SUBSCRIPTION_VIEW",
        "PLATFORM_SUBSCRIPTION_ADJUST","PLATFORM_ANALYTICS_VIEW","PLATFORM_AUDIT_VIEW","PLATFORM_SUPPORT_ACCESS",
        "PLATFORM_CONFIG_MANAGE","PLATFORM_IDENTITY_MANAGE"
    );
    public static Set<String> all(){return PERMISSIONS;}
    public static boolean contains(String permission){return PERMISSIONS.contains(permission);}
}
