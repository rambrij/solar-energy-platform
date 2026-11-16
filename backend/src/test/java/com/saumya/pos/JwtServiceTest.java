package com.saumya.pos;
import com.saumya.pos.identity.JwtService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class JwtServiceTest {
 @Test void signedTokensCarryIssuerAudienceAndShortExpiryAndRejectTampering(){var environment=new MockEnvironment();environment.setActiveProfiles("test");var service=new JwtService("",environment,Clock.systemUTC());var token=service.issue("buyer","family",1L);var decoded=service.decoder().decode(token);assertThat(decoded.getSubject()).isEqualTo("buyer");assertThat(decoded.getAudience()).containsExactly("solar-api");assertThat(decoded.getExpiresAt().getEpochSecond()-decoded.getIssuedAt().getEpochSecond()).isEqualTo(300);assertThatThrownBy(()->service.decoder().decode(token.substring(0,token.lastIndexOf('.')+1)+"invalid")).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);}
 @Test void productionCannotUseEphemeralOrWeakSigningMaterial(){var environment=new MockEnvironment();environment.setActiveProfiles("production");assertThatThrownBy(()->new JwtService("",environment,Clock.systemUTC())).hasMessageContaining("required");assertThatThrownBy(()->new JwtService(java.util.Base64.getEncoder().encodeToString(new byte[8]),environment,Clock.systemUTC())).hasMessageContaining("256");}
}
