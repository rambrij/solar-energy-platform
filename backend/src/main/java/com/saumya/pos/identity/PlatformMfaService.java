package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.GlobalUser;
import com.saumya.pos.identity.internal.GlobalUserRepository;
import com.saumya.pos.identity.internal.PlatformMfaEnrollmentRepository;
import com.saumya.pos.identity.internal.PlatformMfaEnrollmentToken;
import com.saumya.pos.identity.internal.RefreshSessionRepository;
import com.saumya.pos.identity.internal.CustomerRepository;
import com.saumya.pos.identity.internal.PlatformGrantRepository;
import com.saumya.pos.security.VendorMembershipAccess;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.PlatformAuditPort;
import com.saumya.pos.shared.PlatformAuditPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class PlatformMfaService {
    private final GlobalUserRepository users;private final PlatformGrantRepository grants;
    private final PlatformMfaEnrollmentRepository enrollmentTokens;private final RefreshSessionRepository sessions;
    private final CustomerRepository customers;private final VendorMembershipAccess memberships;
    private final PlatformMfaCrypto crypto;private final PlatformTotp totp;private final Clock clock;
    private final AuthorizationContext authorization;private final PlatformAuditPort audit;
    public PlatformMfaService(GlobalUserRepository users,PlatformGrantRepository grants,PlatformMfaEnrollmentRepository enrollmentTokens,
        RefreshSessionRepository sessions,CustomerRepository customers,VendorMembershipAccess memberships,
        PlatformMfaCrypto crypto,PlatformTotp totp,Clock clock,AuthorizationContext authorization,PlatformAuditPort audit){
        this.users=users;this.grants=grants;this.enrollmentTokens=enrollmentTokens;this.sessions=sessions;this.customers=customers;
        this.memberships=memberships;this.crypto=crypto;this.totp=totp;this.clock=clock;this.authorization=authorization;this.audit=audit;
    }
    public record Enrollment(String username,String secret,String otpauthUri,String expiresAt){}
    public record EnrollmentStatus(boolean enrolled){}
    public record IssuedEnrollment(String username,String token,String expiresAt){}
    public IssuedEnrollment issueEnrollment(String targetUsername,String reason){
        var actor=authorization.current();
        if(!actor.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");
        actor.requirePermission("PLATFORM_IDENTITY_MANAGE");
        String target=targetUsername==null?"":targetUsername.strip().toLowerCase(java.util.Locale.ROOT);
        String why=reason==null?"":reason.strip();
        if(!target.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||target.length()>100||why.isBlank()||why.length()>500)
            throw new ResponseStatusException(BAD_REQUEST,"A valid platform identity and reason are required");
        if(actor.username().equalsIgnoreCase(target))
            throw new ResponseStatusException(FORBIDDEN,"MFA enrollment or reset requires a different authorized platform operator");
        ensurePlatformOnly(target);
        GlobalUser user=users.lockedByUsername(target).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Platform identity unavailable"));
        user.resetPlatformMfa();
        sessions.revokeByUsername(target);
        enrollmentTokens.deleteByUsername(target);
        byte[] raw=new byte[32];new java.security.SecureRandom().nextBytes(raw);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiry=clock.instant().plus(Duration.ofMinutes(30));
        enrollmentTokens.saveAndFlush(new PlatformMfaEnrollmentToken(hashToken(token),target,expiry,clock.instant()));
        audit.record(actor.username(),"PLATFORM_MFA_ENROLLMENT_ISSUED","PLATFORM_IDENTITY",target,null,why,null);
        return new IssuedEnrollment(target,token,expiry.toString());
    }
    public Enrollment start(String token) {
        String hash=hashToken(token);
        PlatformMfaEnrollmentToken row=enrollmentTokens.locked(hash).orElseThrow(this::expired);
        String username=row.username();
        if(!row.usableAt(clock.instant()))throw expired();
        ensurePlatformOnly(username);
        var user=users.lockedByUsername(username).orElseThrow(()->expired());
        if(user.platformMfaEnrolled())throw new ResponseStatusException(CONFLICT,"Platform MFA is already enrolled");
        if(row.pendingAt(clock.instant()))
            throw new ResponseStatusException(CONFLICT,"An enrollment is already in progress; wait for it to expire");
        String secret=totp.newSecret(),encrypted=crypto.encrypt(secret);
        var pendingUntil=clock.instant().plus(Duration.ofMinutes(10));
        row.beginPending(encrypted,pendingUntil);
        String uri="otpauth://totp/Saumya%20Solar:"+java.net.URLEncoder.encode(username,StandardCharsets.UTF_8)
            +"?secret="+secret+"&issuer=Saumya%20Solar&algorithm=SHA1&digits=6&period=30";
        return new Enrollment(username,secret,uri,pendingUntil.toString());
    }
    public EnrollmentStatus complete(String token,String code) {
        String hash=hashToken(token);
        PlatformMfaEnrollmentToken row=enrollmentTokens.locked(hash).orElseThrow(this::expired);
        String username=row.username();
        if(!row.usableAt(clock.instant())||!row.pendingAt(clock.instant()))throw expired();
        ensurePlatformOnly(username);
        GlobalUser user=users.lockedByUsername(username).orElseThrow(()->expired());
        if(user.platformMfaEnrolled())throw new ResponseStatusException(CONFLICT,"Platform MFA is already enrolled");
        String secret=crypto.decrypt(row.pendingSecretEnc());
        long counter=totp.verifyCounter(secret,code,clock.instant().getEpochSecond());
        if(counter<0)throw new ResponseStatusException(UNAUTHORIZED,"Invalid MFA verification code");
        user.enrollPlatformMfa(crypto.encrypt(secret),clock.instant(),counter);
        enrollmentTokens.delete(row);
        return new EnrollmentStatus(true);
    }
    public EnrollmentStatus status(String username) {
        var actor=authorization.current();
        if(!actor.isPlatformUser()||!actor.username().equals(username))throw new ResponseStatusException(FORBIDDEN,"Platform actor does not match authenticated identity");
        actor.requirePermission("PLATFORM_AUDIT_VIEW");
        var user=users.findByUsername(username).orElseThrow(()->new ResponseStatusException(UNAUTHORIZED,"Identity unavailable"));
        ensurePlatformOnly(username);return new EnrollmentStatus(user.platformMfaEnrolled());
    }
    private void ensurePlatformOnly(String username) {
        if(grants.permissions(username).isEmpty()||memberships.hasMembership(username))
            throw new ResponseStatusException(FORBIDDEN,"A separate platform identity is required");
        if(customers.profileCount(username)>0)
            throw new ResponseStatusException(FORBIDDEN,"A separate platform identity is required");
    }
    private String hashToken(String token) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw expired();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private ResponseStatusException expired(){return new ResponseStatusException(GONE,"Platform MFA enrollment authorization is unavailable or expired");}
}
