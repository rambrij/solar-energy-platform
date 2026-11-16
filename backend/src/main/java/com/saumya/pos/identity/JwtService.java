package com.saumya.pos.identity;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Component;
@Component
public class JwtService {
 private final JwtEncoder encoder;private final NimbusJwtDecoder decoder;private final Clock clock;
 public JwtService(@Value("${auth.jwt-secret:}") String configured,Environment environment,Clock clock){
  this.clock=clock;byte[] material;
  if(configured.isBlank()){
   var profiles=Set.of(environment.getActiveProfiles());if(Collections.disjoint(profiles,Set.of("local","test"))||profiles.contains("prod")||profiles.contains("production"))throw new IllegalStateException("AUTH_JWT_SECRET is required outside local/test");
   material=new byte[32];new java.security.SecureRandom().nextBytes(material);
  }else{try{material=Base64.getDecoder().decode(configured);}catch(IllegalArgumentException e){throw new IllegalStateException("AUTH_JWT_SECRET must be base64 encoded");}if(material.length<32)throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 256 bits");}
  var key=new SecretKeySpec(material,"HmacSHA256");encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  OAuth2TokenValidator<Jwt> audience=jwt->jwt.getAudience().contains("solar-api")?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("solar-energy-platform"),audience));
 }
 public JwtDecoder decoder(){return decoder;}
 public String issue(String username,String family,Long tenant){var now=clock.instant();var claims=JwtClaimsSet.builder().issuer("solar-energy-platform").subject(username).audience(List.of("solar-api")).issuedAt(now).expiresAt(now.plusSeconds(300)).id(UUID.randomUUID().toString()).claim("family",family);if(tenant!=null)claims.claim("tenant",tenant);return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims.build())).getTokenValue();}
}
