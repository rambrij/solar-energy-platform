package com.saumya.pos;

import com.saumya.pos.identity.PlatformMfaCrypto;
import com.saumya.pos.identity.PlatformTotp;
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
}
