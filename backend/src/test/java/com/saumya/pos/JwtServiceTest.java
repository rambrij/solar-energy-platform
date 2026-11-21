package com.saumya.pos;

import com.saumya.pos.identity.AuthenticationService;
import com.saumya.pos.identity.JwtService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
 @Test void vendorTokenBindsActorTenantAndMembershipAndRejectsTampering(){
  var env=new MockEnvironment();env.setActiveProfiles("test");
  var service=new JwtService("",env,Clock.systemUTC());
  var token=service.issue("employee","family",AuthenticationService.ActorType.VENDOR,1L,"1:employee");
  var decoded=service.decoder().decode(token);
  assertThat(decoded.getSubject()).isEqualTo("employee");
  assertThat(decoded.getAudience()).containsExactly("solar-api");
  assertThat(decoded.getExpiresAt().getEpochSecond()-decoded.getIssuedAt().getEpochSecond()).isEqualTo(300);
  assertThat(decoded.getClaimAsString("actor_type")).isEqualTo("VENDOR");
  assertThat(((Number)decoded.getClaim("tenant")).longValue()).isEqualTo(1L);
  assertThat(decoded.getClaimAsString("membership_id")).isEqualTo("1:employee");
  assertThatThrownBy(()->service.decoder().decode(token.substring(0,token.lastIndexOf('.')+1)+"invalid"))
    .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
 }
 @Test void customerAndPlatformTokensHaveNoTenantOrMembershipClaims(){
  var env=new MockEnvironment();env.setActiveProfiles("test");
  var service=new JwtService("",env,Clock.systemUTC());
  var customer=service.decoder().decode(service.issue("buyer","customer-family",AuthenticationService.ActorType.CUSTOMER,null,null));
  assertThat(customer.getClaimAsString("actor_type")).isEqualTo("CUSTOMER");
  assertThat(customer.getClaims().containsKey("tenant")).isFalse();
  assertThat(customer.getClaimAsString("membership_id")).isNull();
  var platform=service.decoder().decode(service.issue("admin","platform-family",AuthenticationService.ActorType.PLATFORM,null,null));
  assertThat(platform.getClaimAsString("actor_type")).isEqualTo("PLATFORM");
  assertThat(platform.getClaims().containsKey("tenant")).isFalse();
  assertThat(platform.getClaimAsString("membership_id")).isNull();
 }
 @Test void actorScopeCannotBeOmittedOrContradictTheActorType(){
  var env=new MockEnvironment();env.setActiveProfiles("test");
  var service=new JwtService("",env,Clock.systemUTC());
  assertThatThrownBy(()->service.issue("employee","bad",AuthenticationService.ActorType.VENDOR,1L,null))
    .isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.issue("buyer","bad",AuthenticationService.ActorType.CUSTOMER,1L,null))
    .isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.issue("admin","bad",AuthenticationService.ActorType.PLATFORM,null,"1:admin"))
    .isInstanceOf(IllegalArgumentException.class);
 }
 @Test void productionCannotUseEphemeralOrWeakSigningMaterial(){
  var env=new MockEnvironment();env.setActiveProfiles("production");
  assertThatThrownBy(()->new JwtService("",env,Clock.systemUTC())).hasMessageContaining("required");
  assertThatThrownBy(()->new JwtService(java.util.Base64.getEncoder().encodeToString(new byte[8]),env,Clock.systemUTC()))
    .hasMessageContaining("256");
 }
}
