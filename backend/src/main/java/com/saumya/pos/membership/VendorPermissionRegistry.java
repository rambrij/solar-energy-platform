package com.saumya.pos.membership;

import java.util.*;
/** Single allow-list for permissions that tenant-owned vendor roles can contain. */
public final class VendorPermissionRegistry {
    private static final Set<String> EXTRA=Set.of(
        "POS_RETURN_VIEW","POS_REFUND_VIEW","POS_REPORT_EXPORT","POS_SETTINGS_MANAGE",
        "POS_TENDER_EXTERNAL_RECORD","POS_TENDER_EXTERNAL_CONFIRM","POS_TENDER_MANUAL_VERIFY",
        "POS_REFUND_CASH_RECORD","POS_REFUND_PRINT","POS_RETURN_INSPECT","POS_RETURN_AUDIT_VIEW",
        "POS_TENDER_VIEW"
    );
    private static final Set<String> KNOWN;
    static {
        Set<String> all=new HashSet<>(MembershipService.VENDOR_ADMIN_PERMISSIONS);
        all.addAll(EXTRA);KNOWN=Collections.unmodifiableSet(all);
    }
    private VendorPermissionRegistry(){}
    public static Set<String> all(){return KNOWN;}
    public static String requireVendorPermission(String value){
        String p=value==null?"":value.strip().toUpperCase(Locale.ROOT);
        if(p.startsWith("PLATFORM_")||!KNOWN.contains(p))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Unknown or non-vendor permission: "+p);
        return p;
    }
}
