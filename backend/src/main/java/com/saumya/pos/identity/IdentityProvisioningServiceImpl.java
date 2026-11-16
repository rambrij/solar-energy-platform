package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class IdentityProvisioningServiceImpl implements IdentityProvisioningService {
    private final GlobalUserRepository users;
    private final IdentitySequenceRepository sequences;
    private final IdentityInvitationRepository invitations;
    private final PasswordEncoder passwords;
    private final Clock clock;
    public IdentityProvisioningServiceImpl(GlobalUserRepository users,IdentitySequenceRepository sequences,
        IdentityInvitationRepository invitations,PasswordEncoder passwords,Clock clock){
        this.users=users;this.sequences=sequences;this.invitations=invitations;this.passwords=passwords;this.clock=clock;
    }

    @Override public Invitation issueInvitation(String requested){
        String username=normalize(requested);
        var existing=users.findByUsername(username);
        if(existing.isPresent()&&existing.get().enabled())return new Invitation(username,null,null,true);
        if(existing.isEmpty()){
            long id=sequences.locked().orElseThrow(()->new IllegalStateException("Identity sequence unavailable")).allocate();
            users.saveAndFlush(new GlobalUser(id,username,passwords.encode(UUID.randomUUID().toString()),false));
        }
        invitations.deleteByUsername(username);
        String token=randomToken();
        Instant now=clock.instant(),expires=now.plus(Duration.ofHours(48));
        invitations.saveAndFlush(new IdentityInvitation(hash(token),username,expires,now));
        return new Invitation(username,token,expires,false);
    }

    @Override public void acceptInvitation(String rawToken,String newPassword){
        if(rawToken==null||!rawToken.matches("[A-Za-z0-9_-]{43}")||newPassword==null||
            newPassword.length()<12||newPassword.getBytes(StandardCharsets.UTF_8).length>72){
            throw new ResponseStatusException(BAD_REQUEST,"Valid invitation token and a 12 to 72 byte password are required");
        }
        var invitation=invitations.locked(hash(rawToken)).orElseThrow(()->new ResponseStatusException(GONE,"Invitation unavailable or expired"));
        if(!invitation.usableAt(clock.instant()))throw new ResponseStatusException(GONE,"Invitation unavailable or expired");
        var user=users.findByUsername(invitation.username()).orElseThrow(()->new ResponseStatusException(GONE,"Invitation unavailable"));
        if(user.enabled())throw new ResponseStatusException(CONFLICT,"Account has already been activated");
        user.activate(passwords.encode(newPassword));
        invitation.consume(clock.instant());
    }

    private static String normalize(String value){
        String normalized=value==null?"":value.strip().toLowerCase(Locale.ROOT);
        if(normalized.length()>100||!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ResponseStatusException(BAD_REQUEST,"A valid Vendor Admin email is required");
        return normalized;
    }
    private static String randomToken(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
