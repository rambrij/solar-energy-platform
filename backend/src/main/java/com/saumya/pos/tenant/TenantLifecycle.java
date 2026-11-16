package com.saumya.pos.tenant;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import static com.saumya.pos.tenant.TenantStatus.*;

/**
 * Pure tenant lifecycle rules. PENDING -> UNDER_REVIEW -> ACTIVE -> SUSPENDED/CLOSED, with reactivation
 * SUSPENDED -> ACTIVE and return-to-pending from review. CLOSED is terminal (TEN-004: closing is controlled and
 * non-cascading; financial and audit records are preserved by never deleting anything).
 */
public final class TenantLifecycle {
    public static final int MAX_REASON=500;
    private static final Map<TenantStatus,Set<TenantStatus>> ALLOWED=new EnumMap<>(TenantStatus.class);
    static {
        ALLOWED.put(PENDING,Set.of(UNDER_REVIEW,CLOSED));
        ALLOWED.put(UNDER_REVIEW,Set.of(ACTIVE,PENDING,CLOSED));
        ALLOWED.put(ACTIVE,Set.of(SUSPENDED,CLOSED));
        ALLOWED.put(SUSPENDED,Set.of(ACTIVE,CLOSED));
        ALLOWED.put(CLOSED,Set.of());
    }
    private TenantLifecycle(){}

    public static Set<TenantStatus> next(TenantStatus from){return ALLOWED.get(from);}
    public static boolean canTransition(TenantStatus from,TenantStatus to){return from!=null&&to!=null&&ALLOWED.get(from).contains(to);}
    /** Vendor operations (login scope, storefront, checkout) are allowed only while ACTIVE (SRS 7.3). */
    public static boolean vendorOperationsAllowed(TenantStatus status){return status==ACTIVE;}

    /** Validates a requested transition; every transition needs a recorded reason. */
    public static void require(TenantStatus from,TenantStatus to,String reason){
        if(from==null||to==null)throw new TenantException(TenantException.Kind.INVALID,"Tenant status is required");
        if(reason==null||reason.isBlank())throw new TenantException(TenantException.Kind.INVALID,"A reason is required for every lifecycle change");
        if(reason.strip().length()>MAX_REASON)throw new TenantException(TenantException.Kind.INVALID,"Reason must be at most "+MAX_REASON+" characters");
        if(from==to)throw new TenantException(TenantException.Kind.CONFLICT,"Tenant is already "+from);
        if(!canTransition(from,to))throw new TenantException(TenantException.Kind.CONFLICT,"Cannot move a tenant from "+from+" to "+to);
    }
}
