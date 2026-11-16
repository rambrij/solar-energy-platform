package com.saumya.pos.identity;

import java.util.Set;

/** Platform permission catalogue (SRS 11.2). Platform and vendor permissions are disjoint. */
public final class PlatformPermissions {
    public static final String TENANT_CREATE="PLATFORM_TENANT_CREATE",TENANT_UPDATE="PLATFORM_TENANT_UPDATE",TENANT_SUSPEND="PLATFORM_TENANT_SUSPEND",TENANT_CLOSE="PLATFORM_TENANT_CLOSE",
        PLAN_MANAGE="PLATFORM_PLAN_MANAGE",SUBSCRIPTION_VIEW="PLATFORM_SUBSCRIPTION_VIEW",SUBSCRIPTION_ADJUST="PLATFORM_SUBSCRIPTION_ADJUST",
        ANALYTICS_VIEW="PLATFORM_ANALYTICS_VIEW",AUDIT_VIEW="PLATFORM_AUDIT_VIEW",SUPPORT_ACCESS="PLATFORM_SUPPORT_ACCESS";
    public static final Set<String> ALL=Set.of(TENANT_CREATE,TENANT_UPDATE,TENANT_SUSPEND,TENANT_CLOSE,PLAN_MANAGE,SUBSCRIPTION_VIEW,SUBSCRIPTION_ADJUST,ANALYTICS_VIEW,AUDIT_VIEW,SUPPORT_ACCESS);
    private PlatformPermissions(){}
    public static boolean isPlatform(String permission){return permission!=null&&permission.startsWith("PLATFORM_");}
}
