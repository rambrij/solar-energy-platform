package com.saumya.pos;

import com.saumya.pos.identity.PlatformMfaCrypto;
import com.saumya.pos.identity.PlatformTotp;
import com.saumya.pos.identity.PlatformMfaService;
import com.saumya.pos.identity.internal.GlobalUserRepository;
import com.saumya.pos.identity.internal.PlatformGrantRepository;
import com.saumya.pos.identity.internal.PlatformMfaEnrollmentRepository;
import com.saumya.pos.identity.internal.RefreshSessionRepository;
import com.saumya.pos.identity.internal.CustomerRepository;
import com.saumya.pos.security.VendorMembershipAccess;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.PlatformAuditPort;
import java.time.Clock;
import java.util.Set;
import static org.mockito.Mockito.*;

import com.saumya.pos.identity.internal.GlobalUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class PlatformMfaTest {
    @Test void totpAcceptsKnownRfc6238CodeAndReturnsTheVerifiedCounter(){
        PlatformTotp totp=new PlatformTotp();
        String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(totp.verifyCounter(secret,"287082",59)).isEqualTo(1);
        assertThat(totp.verifyCounter(secret,"000000",59)).isEqualTo(-1);
        assertThat(totp.verifyCounter(secret,"28a082",59)).isEqualTo(-1);
    }
    @Test void encryptedMfaSecretRoundTripsWithoutPersistingPlaintext(){
        MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles("test");
        PlatformMfaCrypto crypto=new PlatformMfaCrypto("",environment);
        String secret="JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        String ciphertext=crypto.encrypt(secret);
        assertThat(ciphertext).isNotEqualTo(secret);
        assertThat(crypto.decrypt(ciphertext)).isEqualTo(secret);
    }
    @Test void platformMfaFailureLocksAfterFiveAttemptsAndSuccessfulVerificationResetsCounter(){
        var user=new GlobalUser(1,"platform@example.test","hash");
        user.enrollPlatformMfa("encrypted",java.time.Instant.EPOCH,5);
        var now=java.time.Instant.parse("2026-11-16T00:00:00Z");
        for(int i=0;i<5;i++)user.platformMfaFailure(now);
        assertThat(user.platformMfaFailedAttempts()).isZero();
        assertThat(user.platformMfaLocked(now)).isTrue();
        assertThat(user.platformMfaLockedUntil()).isEqualTo(now.plusSeconds(900));
        user.platformMfaSuccess();
        assertThat(user.platformMfaLocked(now)).isFalse();
        assertThat(user.platformMfaFailedAttempts()).isZero();
    }
    @Test void previouslyUsedTotpCounterCannotBeAcceptedAgain(){
        var user=new GlobalUser(1,"platform@example.test","hash");
        user.enrollPlatformMfa("encrypted",java.time.Instant.EPOCH,9);
        assertThatThrownBy(()->user.acceptPlatformMfaCounter(9)).isInstanceOf(IllegalStateException.class);
        user.acceptPlatformMfaCounter(10);
        assertThat(user.platformMfaLastCounter()).isEqualTo(10);
    }

    @Test
    void platformMfaServiceRejectsVendorDirectCallsBeforeAnyRepositoryOrAuditAccess() {
        var users=mock(GlobalUserRepository.class);
        var grants=mock(PlatformGrantRepository.class);
        var tokens=mock(PlatformMfaEnrollmentRepository.class);
        var sessions=mock(RefreshSessionRepository.class);
        var customers=mock(CustomerRepository.class);
        var memberships=mock(VendorMembershipAccess.class);
        var crypto=mock(PlatformMfaCrypto.class);
        var totp=mock(PlatformTotp.class);
        var authorization=mock(AuthorizationContext.class);
        var audit=mock(PlatformAuditPort.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "vendor@example.test",7L,12L,"7:vendor@example.test",Set.of("POS_VIEW"),false,false,true));
        var service=new PlatformMfaService(users,grants,tokens,sessions,customers,memberships,
            crypto,totp,Clock.systemUTC(),authorization,audit);

        assertThatThrownBy(()->service.issueEnrollment("operator@platform.test","P0-02 direct-call denial"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Platform identity required");
        assertThatThrownBy(()->service.status("vendor@example.test"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Platform actor does not match");

        verifyNoInteractions(users,grants,tokens,sessions,customers,memberships,crypto,totp,audit);
    }

    @Test
    void platformMfaTokenEntryPointsRejectMalformedTokensBeforeAnyPersistenceOrCryptoAdapter() {
        var users=mock(GlobalUserRepository.class);
        var grants=mock(PlatformGrantRepository.class);
        var tokens=mock(PlatformMfaEnrollmentRepository.class);
        var sessions=mock(RefreshSessionRepository.class);
        var customers=mock(CustomerRepository.class);
        var memberships=mock(VendorMembershipAccess.class);
        var crypto=mock(PlatformMfaCrypto.class);
        var totp=mock(PlatformTotp.class);
        var authorization=mock(AuthorizationContext.class);
        var audit=mock(PlatformAuditPort.class);
        var service=new PlatformMfaService(users,grants,tokens,sessions,customers,memberships,
            crypto,totp,Clock.systemUTC(),authorization,audit);

        assertThatThrownBy(()->service.start("not-a-valid-enrollment-token"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.complete("not-a-valid-enrollment-token","123456"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verifyNoInteractions(users,grants,tokens,sessions,customers,memberships,crypto,totp,audit);
    }
}
