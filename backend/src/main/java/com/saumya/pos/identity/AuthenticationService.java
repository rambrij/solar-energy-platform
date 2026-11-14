package com.saumya.pos.identity;
import com.saumya.pos.identity.internal.*;
import com.saumya.pos.membership.MembershipService;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service
public class AuthenticationService {
 private final GlobalUserRepository users;private final CustomerRepository customers;private final IdentitySequenceRepository sequence;private final RefreshSessionRepository sessions;private final MembershipService memberships;private final PasswordEncoder passwords;private final JwtService jwt;private final Clock clock;private final String dummy;
 public AuthenticationService(GlobalUserRepository users,CustomerRepository customers,IdentitySequenceRepository sequence,RefreshSessionRepository sessions,MembershipService memberships,PasswordEncoder passwords,JwtService jwt,Clock clock){this.users=users;this.customers=customers;this.sequence=sequence;this.sessions=sessions;this.memberships=memberships;this.passwords=passwords;this.jwt=jwt;this.clock=clock;dummy=passwords.encode(UUID.randomUUID().toString());}
 public record Account(String username,Long tenantId,Long branchId,List<String> permissions){}
 public record Session(String accessToken,int expiresIn,Account account,String refreshToken){}
 private ResponseStatusException denied(){return new ResponseStatusException(UNAUTHORIZED,"Invalid or expired authentication");}
 private String normalize(String username){return username==null?"":username.strip().toLowerCase(Locale.ROOT);}
 private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 private Account account(String username){var grants=new ArrayList<String>();if(customers.resolve(username).isPresent())grants.add("CUSTOMER");var membership=memberships.access(username);membership.ifPresent(m->grants.addAll(m.permissions()));if(grants.isEmpty()&&membership.isEmpty())throw denied();return new Account(username,membership.map(MembershipService.Access::tenant).orElse(null),membership.map(MembershipService.Access::branch).orElse(null),List.copyOf(grants));}
 private Session issue(String username,String family){var account=account(username);String refresh=random();sessions.saveAndFlush(new RefreshSession(hash(refresh),username,family,clock.instant().plusSeconds(604800)));return new Session(jwt.issue(username,family,account.tenantId()),300,account,refresh);}
 @Transactional public Session login(String username,String password){var user=users.findByUsername(normalize(username));boolean valid=password!=null&&passwords.matches(password,user.map(GlobalUser::password).orElse(dummy));if(!valid||user.isEmpty()||!user.get().enabled())throw denied();return issue(user.get().username(),UUID.randomUUID().toString());}
 @Transactional public Session register(String username,String password){String normalized=normalize(username);if(normalized.length()>100||!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(BAD_REQUEST,"Valid email and password of 12 to 72 UTF-8 bytes required");var counter=sequence.locked().orElseThrow();if(users.findByUsername(normalized).isPresent())throw new ResponseStatusException(CONFLICT,"Registration unavailable");long id=counter.allocate();users.saveAndFlush(new GlobalUser(id,normalized,passwords.encode(password)));customers.saveAndFlush(new CustomerProfile(id,id));return issue(normalized,UUID.randomUUID().toString());}
 @Transactional(noRollbackFor=ResponseStatusException.class) public Session refresh(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw denied();var current=sessions.locked(hash(token)).orElseThrow(this::denied);if(current.spent()||!current.expires().isAfter(clock.instant())){sessions.revoke(current.family());throw denied();}current.consume(clock.instant());var user=users.findByUsername(current.username()).filter(GlobalUser::enabled);if(user.isEmpty()){sessions.revoke(current.family());throw denied();}return issue(current.username(),current.family());}
 @Transactional public void logout(String token){if(token!=null&&token.matches("[A-Za-z0-9_-]{43}"))sessions.locked(hash(token)).ifPresent(s->sessions.revoke(s.family()));}
 @Transactional(readOnly=true) public org.springframework.security.authentication.AbstractAuthenticationToken authenticate(org.springframework.security.oauth2.jwt.Jwt token){String username=token.getSubject(),family=token.getClaimAsString("family");if(family==null||!sessions.existsByFamilyAndRevokedFalseAndExpiresAfter(family,clock.instant())||users.findByUsername(username).filter(GlobalUser::enabled).isEmpty())throw new BadCredentialsException("Session unavailable");Account current;try{current=account(username);}catch(ResponseStatusException e){throw new BadCredentialsException("Identity unavailable");}var permissions=new ArrayList<String>();if(current.permissions().contains("CUSTOMER"))permissions.add("CUSTOMER");Number tenant=token.getClaim("tenant");if(tenant!=null){if(!Objects.equals(current.tenantId(),tenant.longValue()))throw new BadCredentialsException("Membership unavailable");permissions.addAll(current.permissions().stream().filter(p->!p.equals("CUSTOMER")).toList());}return UsernamePasswordAuthenticationToken.authenticated(username,null,permissions.stream().map(SimpleGrantedAuthority::new).toList());}
 @Transactional(readOnly=true) public Account me(String username){return account(username);}
}
