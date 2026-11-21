package com.saumya.pos;
import com.saumya.pos.identity.*;
import com.saumya.pos.identity.internal.*;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.security.ActorSessionDetails;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
class AuthenticationServiceTest {
 @SuppressWarnings("unchecked") private <T>T repository(Class<T> type,java.lang.reflect.InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
 @Test void validGlobalCustomerLoginNormalizesIdentityAndPersistsOnlyHashedRefreshMaterial(){var encoder=new BCryptPasswordEncoder(4);var user=new GlobalUser(7,"buyer@example.com",encoder.encode("PasswordForTests"));var saved=new ArrayList<RefreshSession>();var users=repository(GlobalUserRepository.class,(p,m,a)->{assertThat(a[0]).isEqualTo("buyer@example.com");return Optional.of(user);});var customers=repository(CustomerRepository.class,(p,m,a)->Optional.of(7L));var sessions=repository(RefreshSessionRepository.class,(p,m,a)->{saved.add((RefreshSession)a[0]);return a[0];});var membership=new MembershipService(null,null,null){public Optional<Access> access(String name){return Optional.empty();}};var environment=new MockEnvironment();environment.setActiveProfiles("test");var service=new AuthenticationService(users,customers,null,sessions,membership,encoder,new JwtService("",environment,Clock.systemUTC()),Clock.systemUTC(),repository(PlatformGrantRepository.class,(p,m,a)->List.of()),environment);var result=service.login(" BUYER@EXAMPLE.COM ","PasswordForTests");assertThat(result.account().permissions()).containsExactly("CUSTOMER");assertThat(result.refreshToken()).hasSize(43);assertThat(saved).hasSize(1);assertThat(result.account().tenantId()).isNull();}
 @Test void malformedRegistrationAndRefreshStopBeforePersistence(){var service=new AuthenticationService(null,null,null,null,null,new BCryptPasswordEncoder(4),null,Clock.systemUTC(),null,new MockEnvironment());assertThatThrownBy(()->service.register("invalid","short")).hasMessageContaining("Valid email");assertThatThrownBy(()->service.refresh("not-a-token")).hasMessageContaining("expired");}

 @Test void platformPasswordAccessIsLimitedToExplicitLocalTestUntilMfaExists(){
  var encoder=new BCryptPasswordEncoder(4);var user=new GlobalUser(1,"admin@example.test",encoder.encode("PlatformPassword2026!"));
  var users=repository(GlobalUserRepository.class,(p,m,a)->Optional.of(user));
  var customers=repository(CustomerRepository.class,(p,m,a)->m.getName().equals("profileCount")?0L:Optional.empty());
  var platform=repository(PlatformGrantRepository.class,(p,m,a)->List.of("PLATFORM_TENANT_CREATE"));
  var sessions=repository(RefreshSessionRepository.class,(p,m,a)->a[0]);
  var membership=new MembershipService(null,null,null){public Optional<Access> access(String name){return Optional.empty();}public boolean hasMembership(String name){return false;}};
  for(String[] profiles:List.of(new String[]{},new String[]{"production"},new String[]{"local","prod"},new String[]{"test","production"})){
   var environment=new MockEnvironment();environment.setActiveProfiles(profiles);
   var service=new AuthenticationService(users,customers,null,sessions,membership,encoder,null,Clock.systemUTC(),platform,environment);
   assertThatThrownBy(()->service.login("admin@example.test","PlatformPassword2026!")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
  for(String profile:List.of("local","test")){
   var environment=new MockEnvironment();environment.setActiveProfiles(profile);
   var service=new AuthenticationService(users,customers,null,sessions,membership,encoder,new JwtService("",environment,Clock.systemUTC()),Clock.systemUTC(),platform,environment);
   var account=service.login("admin@example.test","PlatformPassword2026!").account();assertThat(account.tenantId()).isNull();assertThat(account.permissions()).containsExactly("PLATFORM_TENANT_CREATE");
  }
 }

 @Test void authMeReturnsServerResolvedEffectiveVendorPermissions(){
  var encoder=new BCryptPasswordEncoder(4);
  var users=repository(GlobalUserRepository.class,(p,m,a)->Optional.empty());
  var customers=repository(CustomerRepository.class,(p,m,a)->Optional.empty());
  var platform=repository(PlatformGrantRepository.class,(p,m,a)->List.of());
  var membership=new MembershipService(null,null,null){
   public Optional<Access> access(String name){return Optional.of(new Access(7,9,name,List.of("ORDER_VIEW","POS_VIEW")));}
   public boolean hasMembership(String name){return true;}
  };
  var environment=new MockEnvironment();environment.setActiveProfiles("test");
  var service=new AuthenticationService(users,customers,null,null,membership,encoder,
    new JwtService("",environment,Clock.systemUTC()),Clock.systemUTC(),platform,environment);
  var authentication=UsernamePasswordAuthenticationToken.authenticated("employee@example.test",null,List.of());
  authentication.setDetails(new ActorSessionDetails(
    "VENDOR",7L,"7:employee@example.test","family"));
  SecurityContextHolder.getContext().setAuthentication(authentication);
  try{
   var me=service.me("employee@example.test");
   assertThat(me.tenantId()).isEqualTo(7L);
   assertThat(me.permissions()).containsExactly("ORDER_VIEW","POS_VIEW");
  }finally{SecurityContextHolder.clearContext();}
 }

 @Test void dualCustomerVendorIdentityRequiresExplicitSelectionAndNeverCombinesPermissions(){
  var encoder=new BCryptPasswordEncoder(4);
  var username="employee-customer@example.test";
  var user=new GlobalUser(9,username,encoder.encode("DualIdentityPassword2026!"));
  var users=repository(GlobalUserRepository.class,(p,m,a)->m.getName().equals("findByUsername")?Optional.of(user):Optional.of(user));
  var customers=repository(CustomerRepository.class,(p,m,a)->m.getName().equals("resolve")?Optional.of(9L):null);
  var sessions=repository(RefreshSessionRepository.class,(p,m,a)->a[0]);
  var membership=new MembershipService(null,null,null){
   public Optional<Access> access(String name){return Optional.of(new Access(70,701,name,List.of("PRODUCT_VIEW","ORDER_VIEW")));}
   public boolean hasMembership(String name){return true;}
  };
  var platform=repository(PlatformGrantRepository.class,(p,m,a)->List.of());
  var environment=new MockEnvironment();environment.setActiveProfiles("test");
  var service=new AuthenticationService(users,customers,null,sessions,membership,encoder,
    new JwtService("",environment,Clock.systemUTC()),Clock.systemUTC(),platform,environment);

  assertThatThrownBy(()->service.login(username,"DualIdentityPassword2026!"))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
    .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.CONFLICT)
    .hasMessageContaining("both customer and vendor access");

  var customer=service.login(username,"DualIdentityPassword2026!",null,AuthenticationService.ActorType.CUSTOMER,null).account();
  assertThat(customer.actorType()).isEqualTo(AuthenticationService.ActorType.CUSTOMER);
  assertThat(customer.permissions()).containsExactly("CUSTOMER");
  assertThat(customer.availableActorTypes()).containsExactly(AuthenticationService.ActorType.CUSTOMER,AuthenticationService.ActorType.VENDOR);
  assertThat(customer.tenantId()).isNull();
  assertThat(customer.membershipId()).isNull();

  var vendor=service.login(username,"DualIdentityPassword2026!",null,AuthenticationService.ActorType.VENDOR,70L).account();
  assertThat(vendor.actorType()).isEqualTo(AuthenticationService.ActorType.VENDOR);
  assertThat(vendor.permissions()).containsExactly("PRODUCT_VIEW","ORDER_VIEW");
  assertThat(vendor.permissions()).doesNotContain("CUSTOMER");
  assertThat(vendor.availableActorTypes()).containsExactly(AuthenticationService.ActorType.CUSTOMER,AuthenticationService.ActorType.VENDOR);
  assertThat(vendor.tenantId()).isEqualTo(70L);
  assertThat(vendor.membershipId()).isEqualTo("70:"+username);
 }

 @Test void refreshSessionStoresExactlyOneActorAndTheServerResolvedMembershipKey(){
  var vendor=new RefreshSession("hash","employee@example.test","family",java.time.Instant.now().plusSeconds(60),false,"VENDOR",11L,"11:employee@example.test");
  assertThat(vendor.actorType()).isEqualTo("VENDOR");
  assertThat(vendor.tenantId()).isEqualTo(11L);
  assertThat(vendor.membershipId()).isEqualTo("11:employee@example.test");
  var customer=new RefreshSession("hash2","buyer@example.test","family2",java.time.Instant.now().plusSeconds(60),false,"CUSTOMER",null,null);
  assertThat(customer.actorType()).isEqualTo("CUSTOMER");
  assertThat(customer.tenantId()).isNull();
  assertThat(customer.membershipId()).isNull();
 }
}
