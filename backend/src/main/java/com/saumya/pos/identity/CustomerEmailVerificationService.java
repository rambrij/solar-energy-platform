package com.saumya.pos.identity;
import com.saumya.pos.identity.internal.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service public class CustomerEmailVerificationService {
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final Duration LIFETIME=Duration.ofHours(24), COOLDOWN=Duration.ofSeconds(60);
 private final IdentityEmailVerificationTokenRepository tokens;
 private final GlobalUserRepository users;
 private final CustomerRepository customers;
 private final JavaMailSender mail;
 private final Clock clock;
 private final String publicUrl,from;
 public CustomerEmailVerificationService(IdentityEmailVerificationTokenRepository tokens,GlobalUserRepository users,
  CustomerRepository customers,JavaMailSender mail,Clock clock,
  @Value("${app.public-url:http://localhost:8080}") String publicUrl,
  @Value("${app.mail.from:noreply@somya.local}") String from){
  this.tokens=tokens;this.users=users;this.customers=customers;this.mail=mail;this.clock=clock;
  this.publicUrl=normalizeUrl(publicUrl);this.from=from;
 }
 @Transactional public void sendInitial(GlobalUser user){
  if(user==null||!user.enabled()||user.emailVerified())throw new IllegalArgumentException("Pending enabled customer required");
  Issued issued=issue(user);
  try{send(issued,user.username());}catch(MailException ex){
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email verification could not be sent; registration was not completed",ex);
  }
 }
 @Transactional public void resend(String email){
  String normalized=normalize(email);if(normalized.isBlank()||normalized.length()>100)return;
  Optional<GlobalUser> candidate=users.lockedByUsername(normalized);if(candidate.isEmpty())return;
  GlobalUser user=candidate.get();if(!user.enabled()||user.emailVerified()||customers.profileCount(normalized)==0)return;
  Optional<IdentityEmailVerificationToken> previous=tokens.findFirstByUserIdOrderByCreatedAtDesc(user.id());
  Instant now=clock.instant();if(previous.isPresent()&&previous.get().createdAt().plus(COOLDOWN).isAfter(now))return;
  previous.ifPresent(t->t.cancel(now));Issued issued=issue(user);
  try{send(issued,normalized);}catch(MailException ex){issued.token().cancel(now);}
 }
 @Transactional public void verify(String raw){
  if(raw==null||!raw.matches("[A-Za-z0-9_-]{43}"))throw unavailable();
  String tokenHash=hash(raw);
  IdentityEmailVerificationToken observed=tokens.findById(tokenHash).orElseThrow(CustomerEmailVerificationService::unavailable);
  GlobalUser user=users.lockedById(observed.userId()).orElseThrow(CustomerEmailVerificationService::unavailable);
  Instant now=clock.instant();IdentityEmailVerificationToken token=tokens.lockedByHash(tokenHash).orElseThrow(CustomerEmailVerificationService::unavailable);
  if(!user.enabled()||customers.profileCount(user.username())==0||token.cancelledAt()!=null)throw unavailable();
  if(token.consumedAt()!=null){if(user.emailVerified())return;throw unavailable();}
  if(!token.usableAt(now))throw unavailable();token.consume(now);user.verifyEmail();
 }
 private Issued issue(GlobalUser user){
  String raw=randomToken();Instant now=clock.instant();
  IdentityEmailVerificationToken token=new IdentityEmailVerificationToken(hash(raw),user.id(),now,now.plus(LIFETIME));
  tokens.saveAndFlush(token);return new Issued(raw,token);
 }
 private void send(Issued issued,String address){
  SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(address);
  message.setSubject("Verify your Saumya Solar Marketplace email");
  message.setText("Please verify your email address to activate your customer account.\n\n"
   +"Open this link within 24 hours: "+publicUrl+"/verify-email?token="+issued.raw()+"\n\n"
   +"If you did not create this account, you can ignore this message.");mail.send(message);
 }
 private static String normalize(String value){return value==null?"":value.strip().toLowerCase(Locale.ROOT);}
 private static String normalizeUrl(String value){
  String base=value==null?"":value.strip().replaceAll("/+$","");URI uri;
  try{uri=URI.create(base);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Valid public application URL required",ex);}
  if(!( "http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
   throw new IllegalArgumentException("Public URL must be an HTTP(S) base URL");return base;
 }
 private static String randomToken(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 private static String hash(String raw){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
 }
 private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.GONE,"Email verification link is invalid or expired");}
 private record Issued(String raw,IdentityEmailVerificationToken token){}
}
