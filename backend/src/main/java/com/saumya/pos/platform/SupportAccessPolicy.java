package com.saumya.pos.platform;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/** Rules for controlled, purpose-bound, time-limited support access. There is no standing or unlimited access. */
public final class SupportAccessPolicy {
    public static final int MIN_REASON=15,MAX_REASON=500,MIN_MINUTES=5,MAX_MINUTES=240;
    private static final Pattern TICKET=Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{2,59}$");
    /** What a grant may read. Each scope is read-only and maps to one audited reader. */
    public enum Scope { VENDOR_AUDIT_READ }
    public static final class Rejected extends RuntimeException {public Rejected(String message){super(message);}}
    private SupportAccessPolicy(){}

    public static void validate(String reason,String ticket,int minutes){
        if(reason==null||reason.strip().length()<MIN_REASON)throw new Rejected("A purpose of at least "+MIN_REASON+" characters is required");
        if(reason.strip().length()>MAX_REASON)throw new Rejected("Purpose must be at most "+MAX_REASON+" characters");
        if(ticket==null||!TICKET.matcher(ticket.strip()).matches())throw new Rejected("A valid ticket/reference is required");
        if(minutes<MIN_MINUTES||minutes>MAX_MINUTES)throw new Rejected("Access must last between "+MIN_MINUTES+" and "+MAX_MINUTES+" minutes");
    }
    public static Instant expiry(Instant grantedAt,int minutes){return grantedAt.plus(Duration.ofMinutes(minutes));}
    /** Active only inside [grantedAt, expiresAt) and while not revoked. */
    public static boolean active(Instant grantedAt,Instant expiresAt,Instant revokedAt,Instant now){
        return revokedAt==null&&!now.isBefore(grantedAt)&&now.isBefore(expiresAt);
    }
}
