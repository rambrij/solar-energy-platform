package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.GlobalUser;
import com.saumya.pos.identity.internal.GlobalUserRepository;
import com.saumya.pos.identity.internal.PlatformGrantRepository;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.platform.PlatformAuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class PlatformMfaService {
    private final JdbcTemplate db;private final GlobalUserRepository users;private final PlatformGrantRepository grants;
    private final MembershipService memberships;private final PlatformMfaCrypto crypto;private final PlatformTotp totp;private final Clock clock;
    private final AuthorizationContext authorization;private final PlatformAuditService audit;
    public PlatformMfaService(JdbcTemplate db,GlobalUserRepository users,PlatformGrantRepository grants,MembershipService memberships,
        PlatformMfaCrypto crypto,PlatformTotp totp,Clock clock,AuthorizationContext authorization,PlatformAuditService audit){
        this.db=db;this.users=users;this.grants=grants;this.memberships=memberships;this.crypto=crypto;this.totp=totp;this.clock=clock;this.authorization=authorization;this.audit=audit;
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
        db.update("UPDATE identity_refresh_tokens SET revoked=TRUE WHERE username=?",target);
        db.update("DELETE FROM platform_mfa_enrollment_tokens WHERE username=?",target);
        byte[] raw=new byte[32];new java.security.SecureRandom().nextBytes(raw);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiry=clock.instant().plus(Duration.ofMinutes(30));
        db.update("INSERT INTO platform_mfa_enrollment_tokens(token_hash,username,expires_at) VALUES(?,?,?)",hashToken(token),target,java.sql.Timestamp.from(expiry));
        audit.record(actor.username(),"PLATFORM_MFA_ENROLLMENT_ISSUED","PLATFORM_IDENTITY",target,null,why,null);
        return new IssuedEnrollment(target,token,expiry.toString());
    }
    public Enrollment start(String token) {
        String hash=hashToken(token);
        var rows=db.queryForList("SELECT username,expires_at,pending_expires_at FROM platform_mfa_enrollment_tokens WHERE token_hash=? FOR UPDATE",hash);
        if(rows.isEmpty())throw expired();
        var row=rows.getFirst();String username=(String)row.get("username");
        var expiry=((java.sql.Timestamp)row.get("expires_at")).toInstant();
        if(!expiry.isAfter(clock.instant()))throw expired();
        ensurePlatformOnly(username);
        var user=users.lockedByUsername(username).orElseThrow(()->expired());
        if(user.platformMfaEnrolled())throw new ResponseStatusException(CONFLICT,"Platform MFA is already enrolled");
        Object pending=row.get("pending_expires_at");
        if(pending!=null&&((java.sql.Timestamp)pending).toInstant().isAfter(clock.instant()))
            throw new ResponseStatusException(CONFLICT,"An enrollment is already in progress; wait for it to expire");
        String secret=totp.newSecret(),encrypted=crypto.encrypt(secret);
        var pendingUntil=clock.instant().plus(Duration.ofMinutes(10));
        db.update("UPDATE platform_mfa_enrollment_tokens SET pending_secret_enc=?,pending_expires_at=? WHERE token_hash=?",
            encrypted,java.sql.Timestamp.from(pendingUntil),hash);
        String uri="otpauth://totp/Saumya%20Solar:"+java.net.URLEncoder.encode(username,StandardCharsets.UTF_8)
            +"?secret="+secret+"&issuer=Saumya%20Solar&algorithm=SHA1&digits=6&period=30";
        return new Enrollment(username,secret,uri,pendingUntil.toString());
    }
    public EnrollmentStatus complete(String token,String code) {
        String hash=hashToken(token);
        var rows=db.queryForList("SELECT username,expires_at,pending_secret_enc,pending_expires_at FROM platform_mfa_enrollment_tokens WHERE token_hash=? FOR UPDATE",hash);
        if(rows.isEmpty())throw expired();
        var row=rows.getFirst();String username=(String)row.get("username");
        if(!((java.sql.Timestamp)row.get("expires_at")).toInstant().isAfter(clock.instant())
            ||row.get("pending_secret_enc")==null||row.get("pending_expires_at")==null
            ||!((java.sql.Timestamp)row.get("pending_expires_at")).toInstant().isAfter(clock.instant()))throw expired();
        ensurePlatformOnly(username);
        GlobalUser user=users.lockedByUsername(username).orElseThrow(()->expired());
        if(user.platformMfaEnrolled())throw new ResponseStatusException(CONFLICT,"Platform MFA is already enrolled");
        String secret=crypto.decrypt((String)row.get("pending_secret_enc"));
        long counter=totp.verifyCounter(secret,code,clock.instant().getEpochSecond());
        if(counter<0)throw new ResponseStatusException(UNAUTHORIZED,"Invalid MFA verification code");
        user.enrollPlatformMfa(crypto.encrypt(secret),clock.instant(),counter);
        db.update("DELETE FROM platform_mfa_enrollment_tokens WHERE token_hash=?",hash);
        return new EnrollmentStatus(true);
    }
    public EnrollmentStatus status(String username) {
        var user=users.findByUsername(username).orElseThrow(()->new ResponseStatusException(UNAUTHORIZED,"Identity unavailable"));
        ensurePlatformOnly(username);return new EnrollmentStatus(user.platformMfaEnrolled());
    }
    private void ensurePlatformOnly(String username) {
        if(grants.permissions(username).isEmpty()||memberships.hasMembership(username))
            throw new ResponseStatusException(FORBIDDEN,"A separate platform identity is required");
        if(db.queryForObject("SELECT COUNT(*) FROM customer_profiles c JOIN users u ON u.id=c.user_id WHERE u.username=?",Long.class,username)>0)
            throw new ResponseStatusException(FORBIDDEN,"A separate platform identity is required");
    }
    private String hashToken(String token) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw expired();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private ResponseStatusException expired(){return new ResponseStatusException(GONE,"Platform MFA enrollment authorization is unavailable or expired");}
}
