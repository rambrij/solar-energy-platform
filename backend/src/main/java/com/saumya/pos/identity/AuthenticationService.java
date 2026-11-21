package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.security.ActorSessionDetails;
import com.saumya.pos.security.VendorMembershipAccess;
import com.saumya.pos.security.VendorMembershipResolver;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AuthenticationService {
 private final GlobalUserRepository users;
 private final CustomerRepository customers;
 private final IdentitySequenceRepository sequence;
 private final RefreshSessionRepository sessions;
 private final VendorMembershipAccess memberships;
 private final PasswordEncoder passwords;
 private final JwtService jwt;
 private final Clock clock;
 private final String dummy;
 private final PlatformGrantRepository platform;
 private final boolean localPlatformAccess;
 @Autowired private PlatformMfaCrypto mfaCrypto;
 @Autowired private PlatformTotp totp;

 public enum ActorType { PLATFORM, VENDOR, CUSTOMER }
 public record Account(String username,Long tenantId,Long branchId,List<String> permissions,ActorType actorType,String membershipId,List<ActorType> availableActorTypes) {}
 public record Session(String accessToken,int expiresIn,Account account,String refreshToken) {}

 @Autowired
 public AuthenticationService(GlobalUserRepository users,CustomerRepository customers,IdentitySequenceRepository sequence,
   RefreshSessionRepository sessions,VendorMembershipAccess memberships,PasswordEncoder passwords,JwtService jwt,
   Clock clock,PlatformGrantRepository platform,org.springframework.core.env.Environment environment) {
  this.users=users;this.customers=customers;this.sequence=sequence;this.sessions=sessions;this.memberships=memberships;
  this.passwords=passwords;this.jwt=jwt;this.clock=clock;this.platform=platform;
  dummy=passwords.encode(UUID.randomUUID().toString());
  var profiles=Set.of(environment.getActiveProfiles());
  localPlatformAccess=!Collections.disjoint(profiles,Set.of("local","test"))&&!profiles.contains("prod")&&!profiles.contains("production");
 }

 private ResponseStatusException denied(){return new ResponseStatusException(UNAUTHORIZED,"Invalid or expired authentication");}
 private ResponseStatusException contextRequired(){return new ResponseStatusException(CONFLICT,"This identity has both customer and vendor access. Select one actor context explicitly.");}
 private String normalize(String username){return username==null?"":username.strip().toLowerCase(Locale.ROOT);}
 private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}

 /** Resolve exactly one actor. Customer and vendor grants are never combined. */
 private Account account(String username,boolean mfaVerified,ActorType requested,Long requestedTenant) {
  boolean customer=customers.resolve(username).isPresent();
  var membership=memberships.resolve(username);
  var platformPermissions=platform.permissions(username);

  if(!platformPermissions.isEmpty()) {
   if(platformPermissions.stream().anyMatch(p->!p.startsWith("PLATFORM_")))throw denied();
   var user=users.findByUsername(username).orElseThrow(this::denied);
   if(!localPlatformAccess&&(!mfaVerified||!user.platformMfaEnrolled()))throw denied();
   // Platform identities are provisioned separately from both commercial identity types.
   if(customer||memberships.hasMembership(username))throw denied();
   if((requested!=null&&requested!=ActorType.PLATFORM)||requestedTenant!=null)throw denied();
   return new Account(username,null,null,List.copyOf(platformPermissions),ActorType.PLATFORM,null,List.of(ActorType.PLATFORM));
  }

  if(requested==ActorType.PLATFORM)throw denied();
  if(requestedTenant!=null&&requested!=ActorType.VENDOR)throw denied();

  ActorType selected=requested;
  if(selected==null) {
   if(customer&&membership.isPresent())throw contextRequired();
   if(customer)selected=ActorType.CUSTOMER;
   else if(membership.isPresent())selected=ActorType.VENDOR;
   else throw denied();
  }

  if(selected==ActorType.CUSTOMER) {
   if(!customer||requestedTenant!=null)throw denied();
   return new Account(username,null,null,List.of("CUSTOMER"),ActorType.CUSTOMER,null,
    membership.isPresent()?List.of(ActorType.CUSTOMER,ActorType.VENDOR):List.of(ActorType.CUSTOMER));
  }

  if(selected==ActorType.VENDOR) {
   var active=membership.filter(m->requestedTenant==null||m.tenant()==requestedTenant).orElseThrow(this::denied);
   var vendorPermissions=List.copyOf(active.permissions());
   if(vendorPermissions.contains("CUSTOMER")||vendorPermissions.stream().anyMatch(p->p.startsWith("PLATFORM_")))throw denied();
   return new Account(username,active.tenant(),active.branch(),vendorPermissions,ActorType.VENDOR,active.id(),
    customer?List.of(ActorType.CUSTOMER,ActorType.VENDOR):List.of(ActorType.VENDOR));
  }

  throw denied();
 }

 private Session issue(Account account,String family,boolean mfaVerified) {
  String refresh=random();
  sessions.saveAndFlush(new RefreshSession(hash(refresh),account.username(),family,
    clock.instant().plusSeconds(604800),mfaVerified,account.actorType().name(),account.tenantId(),account.membershipId()));
  return new Session(jwt.issue(account.username(),family,account.actorType(),account.tenantId(),account.membershipId()),300,account,refresh);
 }
 private Session issue(String username,String family,boolean mfaVerified,ActorType actorType,Long tenantId) {
  return issue(account(username,mfaVerified,actorType,tenantId),family,mfaVerified);
 }

 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Session login(String username,String password){return login(username,password,null,null,null);}
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Session login(String username,String password,String totpCode){return login(username,password,totpCode,null,null);}
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Session login(String username,String password,String totpCode,ActorType actorType,Long tenantId) {
  String normalized=normalize(username);
  var user=users.findByUsername(normalized);
  boolean valid=password!=null&&passwords.matches(password,user.map(GlobalUser::password).orElse(dummy));
  if(!valid||user.isEmpty()||!user.get().enabled())throw denied();

  boolean isPlatform=!platform.permissions(normalized).isEmpty(),mfaVerified=false;
  if(isPlatform) {
   var locked=users.lockedByUsername(normalized).orElseThrow(this::denied);
   if(locked.platformMfaEnrolled()) {
    Instant now=clock.instant();
    if(locked.platformMfaLocked(now))throw denied();
    if(mfaCrypto==null||totp==null){locked.platformMfaFailure(now);throw denied();}
    long counter=totp.verifyCounter(mfaCrypto.decrypt(locked.platformMfaSecretEnc()),totpCode,now.getEpochSecond());
    if(counter<0||counter<=locked.platformMfaLastCounter()){locked.platformMfaFailure(now);throw denied();}
    locked.acceptPlatformMfaCounter(counter);locked.platformMfaSuccess();mfaVerified=true;
   } else if(!localPlatformAccess)throw denied();
  }
  return issue(normalized,UUID.randomUUID().toString(),mfaVerified,actorType,tenantId);
 }

 @Transactional
 public Session register(String username,String password) {
  String normalized=normalize(username);
  if(normalized.length()>100||!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)
   throw new ResponseStatusException(BAD_REQUEST,"Valid email and password of 12 to 72 UTF-8 bytes required");
  var counter=sequence.locked().orElseThrow();
  if(users.findByUsername(normalized).isPresent())throw new ResponseStatusException(CONFLICT,"Registration unavailable");
  long id=counter.allocate();
  users.saveAndFlush(new GlobalUser(id,normalized,passwords.encode(password)));
  customers.saveAndFlush(new CustomerProfile(id,id));
  return issue(normalized,UUID.randomUUID().toString(),false,ActorType.CUSTOMER,null);
 }

 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Session refresh(String token) {
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw denied();
  var current=sessions.locked(hash(token)).orElseThrow(this::denied);
  if(current.spent()||!current.expires().isAfter(clock.instant())){sessions.revoke(current.family());throw denied();}
  var user=users.findByUsername(current.username()).filter(GlobalUser::enabled);
  if(user.isEmpty()){sessions.revoke(current.family());throw denied();}

  ActorType actorType;
  try{actorType=ActorType.valueOf(current.actorType());}
  catch(Exception e){sessions.revoke(current.family());throw denied();}
  Account active;
  try{active=account(current.username(),current.mfaVerified(),actorType,current.tenantId());}
  catch(ResponseStatusException e){sessions.revoke(current.family());throw denied();}
  if(!Objects.equals(active.membershipId(),current.membershipId())){sessions.revoke(current.family());throw denied();}

  current.consume(clock.instant());
  return issue(active,current.family(),current.mfaVerified());
 }

 /**
  * Switches only between CUSTOMER and VENDOR. The refresh cookie must belong to
  * the same authenticated access-token family and its persisted actor scope.
  */
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Session switchContext(String username,String refreshToken,ActorType target,Long targetTenantId) {
  if(refreshToken==null||!refreshToken.matches("[A-Za-z0-9_-]{43}"))throw denied();
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth==null||!auth.isAuthenticated()||!Objects.equals(username,auth.getName()))throw denied();
  if(!(auth.getDetails() instanceof ActorSessionDetails active))throw denied();
  if(ActorType.valueOf(active.actorType())==ActorType.PLATFORM||target==null||target==ActorType.PLATFORM)
   throw new ResponseStatusException(FORBIDDEN,"Platform identity cannot switch into or out of commercial actor context");
  if(active.actorType()==target&&Objects.equals(active.tenantId(),targetTenantId))
   throw new ResponseStatusException(CONFLICT,"Requested actor context is already active");

  var current=sessions.locked(hash(refreshToken)).orElseThrow(this::denied);
  if(current.spent()||!current.expires().isAfter(clock.instant())) {
   sessions.revoke(current.family());
   throw denied();
  }
  if(!Objects.equals(current.username(),username)
    ||!Objects.equals(current.family(),active.family())
    ||!Objects.equals(current.actorType(),active.actorType())
    ||!Objects.equals(current.tenantId(),active.tenantId())
    ||!Objects.equals(current.membershipId(),active.membershipId()))
   throw denied();

  Account targetAccount=account(username,current.mfaVerified(),target,targetTenantId);
  current.consume(clock.instant());
  sessions.revoke(current.family());
  return issue(targetAccount,UUID.randomUUID().toString(),false);
 }

 @Transactional
 public void logout(String token) {
  if(token!=null&&token.matches("[A-Za-z0-9_-]{43}"))
   sessions.locked(hash(token)).ifPresent(s->sessions.revoke(s.family()));
 }

 @Transactional(readOnly=true)
 public org.springframework.security.authentication.AbstractAuthenticationToken authenticate(org.springframework.security.oauth2.jwt.Jwt token) {
  String username=token.getSubject(),family=token.getClaimAsString("family");
  if(family==null||users.findByUsername(username).filter(GlobalUser::enabled).isEmpty())
   throw new BadCredentialsException("Session unavailable");
  var persisted=sessions.activeForFamily(family,clock.instant())
    .filter(s->Objects.equals(username,s.username()))
    .orElseThrow(()->new BadCredentialsException("Session unavailable"));

  ActorType actorType;
  try{actorType=ActorType.valueOf(token.getClaimAsString("actor_type"));}
  catch(Exception e){throw new BadCredentialsException("Actor context unavailable");}
  Number tenantClaim=token.getClaim("tenant");
  Long tenantId=tenantClaim==null?null:tenantClaim.longValue();
  String membershipId=token.getClaimAsString("membership_id");
  if((actorType==ActorType.VENDOR)!=(tenantId!=null)||
    (actorType==ActorType.VENDOR)!=(membershipId!=null))
   throw new BadCredentialsException("Actor scope is incomplete");
  if(!Objects.equals(persisted.actorType(),actorType.name())
    ||!Objects.equals(persisted.tenantId(),tenantId)
    ||!Objects.equals(persisted.membershipId(),membershipId))
   throw new BadCredentialsException("Session actor scope mismatch");

  boolean mfaVerified=persisted.mfaVerified();
  Account current;
  try{current=account(username,mfaVerified,actorType,tenantId);}
  catch(ResponseStatusException e){throw new BadCredentialsException("Identity or membership unavailable");}
  if(!Objects.equals(current.membershipId(),membershipId))throw new BadCredentialsException("Membership identity unavailable");

  var authorities=new ArrayList<String>(current.permissions());
  authorities.add(switch(actorType) {
   case PLATFORM -> AuthorizationContext.ACTOR_CONTEXT_PLATFORM;
   case VENDOR -> AuthorizationContext.ACTOR_CONTEXT_VENDOR;
   case CUSTOMER -> AuthorizationContext.ACTOR_CONTEXT_CUSTOMER;
  });
  var authentication=UsernamePasswordAuthenticationToken.authenticated(username,null,
    authorities.stream().map(SimpleGrantedAuthority::new).toList());
  authentication.setDetails(new ActorSessionDetails(actorType.name(),tenantId,membershipId,family));
  return authentication;
 }

 @Transactional(readOnly=true)
 public Account me(String username) {
  var actor=SecurityContextHolder.getContext().getAuthentication();
  if(actor==null||!actor.isAuthenticated()||"anonymousUser".equals(actor.getPrincipal()))
   throw new ResponseStatusException(UNAUTHORIZED,"Authentication required");
  if(username==null||!actor.getName().equals(username))
   throw new ResponseStatusException(FORBIDDEN,"Account identity must match authenticated principal");
  if(!(actor.getDetails() instanceof ActorSessionDetails details))
   throw new ResponseStatusException(FORBIDDEN,"Explicit actor context required");
  Account current=account(username,true,ActorType.valueOf(details.actorType()),details.tenantId());
  if(!Objects.equals(current.membershipId(),details.membershipId()))
   throw new ResponseStatusException(FORBIDDEN,"Actor membership context changed");
  return current;
 }
}
