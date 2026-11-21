package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.*;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.membership.TenantEntitlementPolicy;
import com.saumya.pos.membership.internal.EmployeeMembershipRepository;
import com.saumya.pos.tenant.TenantLifecycleService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.security.ActorSessionDetails;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class AuthenticationActorContextTest {
 @SuppressWarnings("unchecked")
 private <T>T repository(Class<T> type,java.lang.reflect.InvocationHandler handler){
  return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private static final String USER="dual@example.test";
 private static final String REFRESH="A".repeat(43);
 private final Clock clock=Clock.fixed(Instant.parse("2026-11-21T08:00:00Z"),ZoneOffset.UTC);

 private AuthenticationService service(RefreshSession old,boolean vendorMembershipActive,List<RefreshSession> saved,List<String> revoked){
  var encoder=new BCryptPasswordEncoder(4);
  var user=new GlobalUser(8,USER,encoder.encode("DualActorPassword2026!"));
  var users=repository(GlobalUserRepository.class,(p,m,a)->Optional.of(user));
  var customers=repository(CustomerRepository.class,(p,m,a)->Optional.of(8L));
  var sessions=repository(RefreshSessionRepository.class,(p,m,a)->switch(m.getName()){
   case "locked" -> Optional.of(old);
   case "activeForFamily" -> Optional.of(old);
   case "saveAndFlush" -> {saved.add((RefreshSession)a[0]);yield a[0];}
   case "revoke" -> {revoked.add((String)a[0]);yield 1;}
   default -> throw new UnsupportedOperationException(m.getName());
  });
  var membership=new MembershipService(
      mock(EmployeeMembershipRepository.class),mock(TenantLifecycleService.class),
      mock(JdbcTemplate.class),mock(TenantEntitlementPolicy.class)){
   @Override public Optional<Access> access(String name){
    return vendorMembershipActive?Optional.of(new Access(77,770,name,List.of("PRODUCT_VIEW","ORDER_VIEW"))):Optional.empty();
   }
   @Override public boolean hasMembership(String name){return true;}
  };
  var platform=repository(PlatformGrantRepository.class,(p,m,a)->List.of());
  var environment=new MockEnvironment();environment.setActiveProfiles("test");
  return new AuthenticationService(users,customers,null,sessions,membership,encoder,
      new JwtService("",environment,clock),clock,platform,environment);
 }

 private AuthenticationService platformService(boolean customerProfile,boolean employeeMembership){
  var encoder=new BCryptPasswordEncoder(4);
  var user=new GlobalUser(8,USER,encoder.encode("DualActorPassword2026!"));
  var users=repository(GlobalUserRepository.class,(p,m,a)->Optional.of(user));
  var customers=repository(CustomerRepository.class,(p,m,a)->customerProfile?Optional.of(8L):Optional.empty());
  var sessions=repository(RefreshSessionRepository.class,(p,m,a)->a[0]);
  var membership=new MembershipService(mock(com.saumya.pos.membership.internal.EmployeeMembershipRepository.class),
      mock(com.saumya.pos.tenant.TenantLifecycleService.class),mock(org.springframework.jdbc.core.JdbcTemplate.class),
      mock(com.saumya.pos.membership.TenantEntitlementPolicy.class)){
   @Override public Optional<Access> access(String name){
    return employeeMembership?Optional.of(new Access(77,770,name,List.of("PRODUCT_VIEW"))):Optional.empty();
   }
   @Override public boolean hasMembership(String name){return employeeMembership;}
  };
  var platform=repository(PlatformGrantRepository.class,(p,m,a)->List.of("PLATFORM_SUPPORT_ACCESS"));
  var environment=new MockEnvironment();environment.setActiveProfiles("test");
  return new AuthenticationService(users,customers,null,sessions,membership,encoder,
      new JwtService("",environment,clock),clock,platform,environment);
 }

 private void authenticateCustomer(String family){
  var authorities=List.of(new SimpleGrantedAuthority("CUSTOMER"),
      new SimpleGrantedAuthority(AuthorizationContext.ACTOR_CONTEXT_CUSTOMER));
  var authentication=UsernamePasswordAuthenticationToken.authenticated(USER,null,authorities);
  authentication.setDetails(new ActorSessionDetails(
      "CUSTOMER",null,null,family));
  SecurityContextHolder.getContext().setAuthentication(authentication);
 }

 private void authenticateVendor(String family){
  var authorities=List.of(new SimpleGrantedAuthority("PRODUCT_VIEW"),
      new SimpleGrantedAuthority("ORDER_VIEW"),
      new SimpleGrantedAuthority(AuthorizationContext.ACTOR_CONTEXT_VENDOR));
  var authentication=UsernamePasswordAuthenticationToken.authenticated(USER,null,authorities);
  authentication.setDetails(new ActorSessionDetails(
      "VENDOR",77L,"77:"+USER,family));
  SecurityContextHolder.getContext().setAuthentication(authentication);
 }

 @Test void customerToVendorSwitchValidatesMembershipRotatesFamilyAndRevokesPreviousSession(){
  var old=new RefreshSession("old-hash",USER,"customer-family",clock.instant().plusSeconds(3600),false,"CUSTOMER",null,null);
  var saved=new ArrayList<RefreshSession>();var revoked=new ArrayList<String>();
  var service=service(old,true,saved,revoked);
  authenticateCustomer("customer-family");
  var session=service.switchContext(USER,REFRESH,AuthenticationService.ActorType.VENDOR,77L);
  assertThat(session.account().actorType()).isEqualTo(AuthenticationService.ActorType.VENDOR);
  assertThat(session.account().tenantId()).isEqualTo(77L);
  assertThat(session.account().membershipId()).isEqualTo("77:"+USER);
  assertThat(session.account().permissions()).containsExactly("PRODUCT_VIEW","ORDER_VIEW");
  assertThat(session.account().permissions()).doesNotContain("CUSTOMER");
  assertThat(session.account().availableActorTypes()).containsExactly(AuthenticationService.ActorType.CUSTOMER,AuthenticationService.ActorType.VENDOR);
  assertThat(saved).hasSize(1);
  assertThat(saved.getFirst().actorType()).isEqualTo("VENDOR");
  assertThat(saved.getFirst().tenantId()).isEqualTo(77L);
  assertThat(saved.getFirst().membershipId()).isEqualTo("77:"+USER);
  assertThat(saved.getFirst().family()).isNotEqualTo("customer-family");
  assertThat(revoked).contains("customer-family");
  assertThat(old.spent()).isTrue();
 }

 @Test void vendorToCustomerSwitchRemovesTenantAndMembershipScopeAndRotatesFamily(){
  var old=new RefreshSession("old-hash",USER,"vendor-family",clock.instant().plusSeconds(3600),false,"VENDOR",77L,"77:"+USER);
  var saved=new ArrayList<RefreshSession>();var revoked=new ArrayList<String>();
  var service=service(old,true,saved,revoked);
  authenticateVendor("vendor-family");
  var session=service.switchContext(USER,REFRESH,AuthenticationService.ActorType.CUSTOMER,null);
  assertThat(session.account().actorType()).isEqualTo(AuthenticationService.ActorType.CUSTOMER);
  assertThat(session.account().permissions()).containsExactly("CUSTOMER");
  assertThat(session.account().tenantId()).isNull();
  assertThat(session.account().membershipId()).isNull();
  assertThat(saved).hasSize(1);
  assertThat(saved.getFirst().actorType()).isEqualTo("CUSTOMER");
  assertThat(saved.getFirst().tenantId()).isNull();
  assertThat(saved.getFirst().membershipId()).isNull();
  assertThat(saved.getFirst().family()).isNotEqualTo("vendor-family");
  assertThat(revoked).contains("vendor-family");
  assertThat(old.spent()).isTrue();
 }

 @Test void vendorRefreshCannotFallBackToCustomerWhenMembershipHasBeenRevoked(){
  var old=new RefreshSession("old-hash",USER,"vendor-family",clock.instant().plusSeconds(3600),false,"VENDOR",77L,"77:"+USER);
  var saved=new ArrayList<RefreshSession>();var revoked=new ArrayList<String>();
  var service=service(old,false,saved,revoked);
  assertThatThrownBy(()->service.refresh(REFRESH)).isInstanceOf(ResponseStatusException.class)
      .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.UNAUTHORIZED);
  assertThat(saved).isEmpty();
  assertThat(revoked).contains("vendor-family");
  assertThat(old.spent()).isFalse();
 }

 @Test void customerRefreshRetainsCustomerContextEvenWhenVendorMembershipAlsoExists(){
  var old=new RefreshSession("old-hash",USER,"customer-family",clock.instant().plusSeconds(3600),false,"CUSTOMER",null,null);
  var saved=new ArrayList<RefreshSession>();var revoked=new ArrayList<String>();
  var service=service(old,true,saved,revoked);
  var session=service.refresh(REFRESH);
  assertThat(session.account().actorType()).isEqualTo(AuthenticationService.ActorType.CUSTOMER);
  assertThat(session.account().permissions()).containsExactly("CUSTOMER");
  assertThat(session.account().tenantId()).isNull();
  assertThat(session.account().membershipId()).isNull();
  assertThat(saved).hasSize(1);
  assertThat(saved.getFirst().actorType()).isEqualTo("CUSTOMER");
  assertThat(saved.getFirst().tenantId()).isNull();
  assertThat(saved.getFirst().membershipId()).isNull();
  assertThat(saved.getFirst().family()).isEqualTo("customer-family");
 }

 @Test void platformCannotBeEnteredThroughCommercialContextSwitch(){
  var old=new RefreshSession("old-hash",USER,"customer-family",clock.instant().plusSeconds(3600),false,"CUSTOMER",null,null);
  var saved=new ArrayList<RefreshSession>();var revoked=new ArrayList<String>();
  var service=service(old,true,saved,revoked);
  authenticateCustomer("customer-family");
  assertThatThrownBy(()->service.switchContext(USER,REFRESH,AuthenticationService.ActorType.PLATFORM,null))
      .isInstanceOf(ResponseStatusException.class)
      .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.FORBIDDEN);
  assertThat(saved).isEmpty();assertThat(revoked).isEmpty();
 }

 @Test void platformIdentityCannotAlsoBeACustomerIdentity(){
  assertThatThrownBy(()->platformService(true,false).login(USER,"DualActorPassword2026!"))
      .isInstanceOf(ResponseStatusException.class)
      .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.UNAUTHORIZED);
 }
 @Test void platformIdentityCannotAlsoBeAVendorEmployeeIdentity(){
  assertThatThrownBy(()->platformService(false,true).login(USER,"DualActorPassword2026!"))
      .isInstanceOf(ResponseStatusException.class)
      .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.UNAUTHORIZED);
 }

 @Test void accessTokenActorTenantAndMembershipMustMatchPersistedRefreshFamily(){
  var old=new RefreshSession("old-hash",USER,"customer-family",clock.instant().plusSeconds(3600),false,"CUSTOMER",null,null);
  var saved=new ArrayList<RefreshSession>();var revoked=new ArrayList<String>();
  var service=service(old,true,saved,revoked);
  var mismatched=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
      .header("alg","HS256").subject(USER).issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(300))
      .claim("family","customer-family").claim("actor_type","VENDOR")
      .claim("tenant",77L).claim("membership_id","77:"+USER).build();
  assertThatThrownBy(()->service.authenticate(mismatched))
      .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
      .hasMessageContaining("Session actor scope mismatch");
  assertThat(saved).isEmpty();
 }

 @Test void foreignRefreshCookieCannotRevokeAnotherIdentityRefreshFamily(){
  var foreign=new RefreshSession("foreign-hash","other@example.test","other-family",clock.instant().plusSeconds(3600),false,"CUSTOMER",null,null);
  var saved=new ArrayList<RefreshSession>();var revoked=new ArrayList<String>();
  var service=service(foreign,true,saved,revoked);
  authenticateCustomer("customer-family");
  assertThatThrownBy(()->service.switchContext(USER,REFRESH,AuthenticationService.ActorType.VENDOR,77L))
      .isInstanceOf(ResponseStatusException.class)
      .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.UNAUTHORIZED);
  assertThat(saved).isEmpty();
  assertThat(revoked).isEmpty();
 }
}
