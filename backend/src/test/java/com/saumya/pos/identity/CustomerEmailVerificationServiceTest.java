package com.saumya.pos.identity;
import com.saumya.pos.identity.internal.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerEmailVerificationServiceTest {
 private static final Instant NOW=Instant.parse("2026-11-27T08:00:00Z");
 private static final String EMAIL="buyer@example.test";
 private final IdentityEmailVerificationTokenRepository tokens=mock(IdentityEmailVerificationTokenRepository.class);
 private final GlobalUserRepository users=mock(GlobalUserRepository.class);
 private final CustomerRepository customers=mock(CustomerRepository.class);
 private final org.springframework.mail.javamail.JavaMailSender mail=mock(org.springframework.mail.javamail.JavaMailSender.class);
 private final CustomerEmailVerificationService service=new CustomerEmailVerificationService(tokens,users,customers,mail,Clock.fixed(NOW,ZoneOffset.UTC),"https://shop.example.test","noreply@example.test");
 @Test void storesOnlyTokenHashAndVerificationIsIdempotent() throws Exception {
  var user=new GlobalUser(17,EMAIL,"encoded-password",true,false);
  when(tokens.saveAndFlush(any())).thenAnswer(a->a.getArgument(0));service.sendInitial(user);
  var mailCapture=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(mailCapture.capture());
  var matcher=Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(mailCapture.getValue().getText());assertThat(matcher.find()).isTrue();String raw=matcher.group(1);
  var tokenCapture=org.mockito.ArgumentCaptor.forClass(IdentityEmailVerificationToken.class);verify(tokens).saveAndFlush(tokenCapture.capture());
  var stored=tokenCapture.getValue();assertThat(stored.tokenHash()).isEqualTo(hash(raw)).isNotEqualTo(raw);
  assertThat(stored.expiresAt()).isEqualTo(NOW.plusSeconds(86400));
  when(tokens.findById(stored.tokenHash())).thenReturn(Optional.of(stored));
  when(tokens.lockedByHash(stored.tokenHash())).thenReturn(Optional.of(stored));
  when(users.lockedById(user.id())).thenReturn(Optional.of(user));when(customers.profileCount(EMAIL)).thenReturn(1L);
  service.verify(raw);service.verify(raw);assertThat(user.emailVerified()).isTrue();assertThat(stored.consumedAt()).isEqualTo(NOW);
 }
 @Test void resendDoesNotRevealWhetherAnAddressHasAnAccount(){
  when(users.lockedByUsername(EMAIL)).thenReturn(Optional.empty());
  assertThatCode(()->service.resend(EMAIL)).doesNotThrowAnyException();verifyNoInteractions(mail,tokens);
 }
 @Test void expiredTokensAreRejected() throws Exception{
  String raw="A".repeat(43);var token=new IdentityEmailVerificationToken(hash(raw),17,NOW.minusSeconds(90000),NOW.minusSeconds(3600));
  when(tokens.findById(hash(raw))).thenReturn(Optional.of(token));
  when(tokens.lockedByHash(hash(raw))).thenReturn(Optional.of(token));
  assertThatThrownBy(()->service.verify(raw)).isInstanceOf(ResponseStatusException.class)
   .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.GONE);
  verifyNoInteractions(users,customers);
 }
 private static String hash(String raw) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}
}
