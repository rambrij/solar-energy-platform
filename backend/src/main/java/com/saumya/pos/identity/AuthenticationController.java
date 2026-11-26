package com.saumya.pos.identity;

import java.time.Duration;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/v1/auth")
public class AuthenticationController {
 private final AuthenticationService service;
 private final boolean secure;
 public AuthenticationController(AuthenticationService service,Environment environment) {
  this.service=service;
  var profiles=Set.of(environment.getActiveProfiles());
  secure=Collections.disjoint(profiles,Set.of("local","test"))||profiles.contains("prod")||profiles.contains("production");
 }
 public record Credentials(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=72) String password,
   @Pattern(regexp="[0-9]{6}") String totpCode,AuthenticationService.ActorType actorType,@Positive Long tenantId) {
  public Credentials(String username,String password,String totpCode){this(username,password,totpCode,null,null);}
 }
 public record ContextSelection(@NotNull AuthenticationService.ActorType actorType,@Positive Long tenantId) {}
 private ResponseCookie cookie(String value,Duration age) {
  return ResponseCookie.from("solar-refresh",value).httpOnly(true).secure(secure).sameSite("Strict").path("/api/v1/auth").maxAge(age).build();
 }
 private ResponseEntity<?> response(AuthenticationService.Session session) {
  return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
    .header(HttpHeaders.SET_COOKIE,cookie(session.refreshToken(),Duration.ofDays(7)).toString())
    .body(Map.of("accessToken",session.accessToken(),"expiresIn",session.expiresIn(),"account",session.account()));
 }
 @GetMapping("/csrf") @PreAuthorize("permitAll()")
 public Object csrf(CsrfToken token) {
  return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(Map.of("token",token.getToken()));
 }
 @PostMapping("/login") @PreAuthorize("permitAll()")
 public Object login(@Valid @RequestBody Credentials credentials) {
  return response(service.login(credentials.username(),credentials.password(),credentials.totpCode(),credentials.actorType(),credentials.tenantId()));
 }
 @PostMapping("/register") @PreAuthorize("permitAll()")
 public Object register(@Valid @RequestBody Credentials credentials) {
  return response(service.register(credentials.username(),credentials.password()));
 }
 @PostMapping("/refresh") @PreAuthorize("permitAll()")
 public Object refresh(@CookieValue(name="solar-refresh",required=false) String token) {return response(service.refresh(token));}
 @PostMapping("/context") @PreAuthorize("isAuthenticated()")
 public Object switchContext(java.security.Principal actor,@CookieValue(name="solar-refresh",required=false) String token,
   @Valid @RequestBody ContextSelection selection) {
  return response(service.switchContext(actor.getName(),token,selection.actorType(),selection.tenantId()));
 }
 @PostMapping("/logout") @PreAuthorize("permitAll()")
 public Object logout(@CookieValue(name="solar-refresh",required=false) String token) {
  service.logout(token);
  return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO).toString()).body(Map.of("loggedOut",true));
 }
 @PostMapping("/sessions/revoke-others") @PreAuthorize("hasAuthority('CUSTOMER')")
 public Object revokeCustomerOtherSessions(java.security.Principal actor) {
  service.revokeOtherSessions(actor.getName());
  return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(Map.of("revoked",true));
 }
 @GetMapping("/me") @PreAuthorize("isAuthenticated()")
 public Object me(java.security.Principal actor) {return service.me(actor.getName());}
}
