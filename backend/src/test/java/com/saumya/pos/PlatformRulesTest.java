package com.saumya.pos;

import com.saumya.pos.platform.ConfigurationPolicy;
import com.saumya.pos.platform.ConfigurationPolicy.Key;
import com.saumya.pos.platform.SubscriptionLifecycle;
import com.saumya.pos.platform.SubscriptionLifecycle.State;
import com.saumya.pos.platform.SupportAccessPolicy;
import com.saumya.pos.tenant.TenantException;
import com.saumya.pos.tenant.TenantLifecycle;
import com.saumya.pos.tenant.TenantStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure platform rules: tenant lifecycle, subscription states, support-access policy and configuration protection. */
class PlatformRulesTest {
    // ---- Tenant lifecycle ----
    @Test void tenantHappyPathAndReactivationAreAllowed(){
        for(var step:new TenantStatus[][]{{TenantStatus.PENDING,TenantStatus.UNDER_REVIEW},{TenantStatus.UNDER_REVIEW,TenantStatus.ACTIVE},{TenantStatus.ACTIVE,TenantStatus.SUSPENDED},{TenantStatus.SUSPENDED,TenantStatus.ACTIVE},{TenantStatus.ACTIVE,TenantStatus.CLOSED},{TenantStatus.SUSPENDED,TenantStatus.CLOSED},{TenantStatus.UNDER_REVIEW,TenantStatus.PENDING}})
            assertDoesNotThrow(()->TenantLifecycle.require(step[0],step[1],"Reviewed by operations"),step[0]+"->"+step[1]);
    }
    @Test void tenantInvalidTransitionsAreConflicts(){
        for(var step:new TenantStatus[][]{{TenantStatus.PENDING,TenantStatus.ACTIVE},{TenantStatus.PENDING,TenantStatus.SUSPENDED},{TenantStatus.ACTIVE,TenantStatus.PENDING},{TenantStatus.ACTIVE,TenantStatus.UNDER_REVIEW},{TenantStatus.SUSPENDED,TenantStatus.PENDING}}){
            var e=assertThrows(TenantException.class,()->TenantLifecycle.require(step[0],step[1],"reason"));assertEquals(TenantException.Kind.CONFLICT,e.kind(),step[0]+"->"+step[1]);
        }
    }
    @Test void closedIsTerminalAndNoStateTransitionsToItself(){
        assertTrue(TenantLifecycle.next(TenantStatus.CLOSED).isEmpty());
        for(var to:TenantStatus.values())assertFalse(TenantLifecycle.canTransition(TenantStatus.CLOSED,to));
        for(var s:TenantStatus.values())assertThrows(TenantException.class,()->TenantLifecycle.require(s,s,"same"));
    }
    @Test void everyTransitionNeedsABoundedReason(){
        assertEquals(TenantException.Kind.INVALID,assertThrows(TenantException.class,()->TenantLifecycle.require(TenantStatus.ACTIVE,TenantStatus.SUSPENDED," ")).kind());
        assertEquals(TenantException.Kind.INVALID,assertThrows(TenantException.class,()->TenantLifecycle.require(TenantStatus.ACTIVE,TenantStatus.SUSPENDED,null)).kind());
        assertEquals(TenantException.Kind.INVALID,assertThrows(TenantException.class,()->TenantLifecycle.require(TenantStatus.ACTIVE,TenantStatus.SUSPENDED,"x".repeat(501))).kind());
        assertEquals(TenantException.Kind.INVALID,assertThrows(TenantException.class,()->TenantLifecycle.require(null,TenantStatus.ACTIVE,"r")).kind());
    }
    @Test void onlyActiveTenantsMayOperate(){
        for(var s:TenantStatus.values())assertEquals(s==TenantStatus.ACTIVE,TenantLifecycle.vendorOperationsAllowed(s));
    }

    // ---- Subscription lifecycle ----
    @Test void subscriptionDiagramTransitionsAreExactlyThoseAllowed(){
        Set<String> allowed=Set.of("PENDING>ACTIVE","PENDING>PAST_DUE","ACTIVE>PAST_DUE","ACTIVE>CANCELLED","ACTIVE>CANCEL_AT_PERIOD_END","ACTIVE>EXPIRED","PAST_DUE>GRACE_PERIOD","PAST_DUE>ACTIVE","GRACE_PERIOD>ACTIVE","GRACE_PERIOD>SUSPENDED","SUSPENDED>ACTIVE","SUSPENDED>CANCELLED","CANCEL_AT_PERIOD_END>CANCELLED");
        for(var from:State.values())for(var to:State.values())assertEquals(allowed.contains(from+">"+to),SubscriptionLifecycle.canTransition(from,to),from+">"+to);
    }
    @Test void subscriptionTerminalStatesAndWriteBlocking(){
        assertTrue(SubscriptionLifecycle.terminal(State.CANCELLED));assertTrue(SubscriptionLifecycle.terminal(State.EXPIRED));assertFalse(SubscriptionLifecycle.terminal(State.SUSPENDED));
        Set<State> blocking=EnumSet.of(State.SUSPENDED,State.CANCELLED,State.EXPIRED);
        for(var s:State.values())assertEquals(blocking.contains(s),SubscriptionLifecycle.blocksVendorWrites(s),s.name());
        assertFalse(SubscriptionLifecycle.blocksVendorWrites(State.GRACE_PERIOD),"grace keeps normal access by default");
        assertTrue(SubscriptionLifecycle.effective(State.ACTIVE));assertFalse(SubscriptionLifecycle.effective(State.EXPIRED));
    }

    // ---- Support access ----
    @Test void supportAccessNeedsPurposeTicketAndBoundedDuration(){
        assertDoesNotThrow(()->SupportAccessPolicy.validate("Investigating failed receipt reprint","SUP-1234",30));
        assertThrows(SupportAccessPolicy.Rejected.class,()->SupportAccessPolicy.validate("too short","SUP-1234",30));
        assertThrows(SupportAccessPolicy.Rejected.class,()->SupportAccessPolicy.validate(null,"SUP-1234",30));
        assertThrows(SupportAccessPolicy.Rejected.class,()->SupportAccessPolicy.validate("Investigating failed receipt reprint",null,30));
        assertThrows(SupportAccessPolicy.Rejected.class,()->SupportAccessPolicy.validate("Investigating failed receipt reprint","a b",30));
        assertThrows(SupportAccessPolicy.Rejected.class,()->SupportAccessPolicy.validate("Investigating failed receipt reprint","SUP-1234",4));
        assertThrows(SupportAccessPolicy.Rejected.class,()->SupportAccessPolicy.validate("Investigating failed receipt reprint","SUP-1234",241));
        assertThrows(SupportAccessPolicy.Rejected.class,()->SupportAccessPolicy.validate("x".repeat(501),"SUP-1234",30));
    }
    @Test void supportGrantIsActiveOnlyInsideItsWindowAndUntilRevoked(){
        Instant start=Instant.parse("2026-11-14T10:00:00Z"),end=SupportAccessPolicy.expiry(start,30);
        assertEquals(Instant.parse("2026-11-14T10:30:00Z"),end);
        assertTrue(SupportAccessPolicy.active(start,end,null,start));assertTrue(SupportAccessPolicy.active(start,end,null,end.minusSeconds(1)));
        assertFalse(SupportAccessPolicy.active(start,end,null,end),"expires exactly at the limit");assertFalse(SupportAccessPolicy.active(start,end,null,start.minusSeconds(1)));
        assertFalse(SupportAccessPolicy.active(start,end,start.plusSeconds(60),start.plusSeconds(120)),"revoked");
    }

    // ---- Configuration protection ----
    @Test void secretKeysAcceptOnlyReferencesNeverLiteralSecrets(){
        assertEquals("env:PLATFORM_GATEWAY_KEY",ConfigurationPolicy.validate(Key.SUBSCRIPTION_API_SECRET_REF,"env:PLATFORM_GATEWAY_KEY",true));
        assertEquals("vault:platform/payments/key",ConfigurationPolicy.validate(Key.SUBSCRIPTION_WEBHOOK_SECRET_REF,"vault:platform/payments/key",true));
        for(var literal:new String[]{"sk_live_abcdef123456","password123","env:","env: spaced","http://x"})assertThrows(ConfigurationPolicy.Rejected.class,()->ConfigurationPolicy.validate(Key.SUBSCRIPTION_API_SECRET_REF,literal,false),literal);
    }
    @Test void secretsAreMaskedAndNonSecretsAreNot(){
        assertEquals("env:********",ConfigurationPolicy.mask(Key.SUBSCRIPTION_API_SECRET_REF,"env:PLATFORM_GATEWAY_KEY"));
        assertEquals("********",ConfigurationPolicy.mask(Key.SUBSCRIPTION_API_SECRET_REF,"nocolon"));
        assertEquals("acct_platform_1",ConfigurationPolicy.mask(Key.SUBSCRIPTION_MERCHANT_ID,"acct_platform_1"));assertNull(ConfigurationPolicy.mask(Key.SUBSCRIPTION_MERCHANT_ID,null));
        assertTrue(ConfigurationPolicy.secret(Key.SUBSCRIPTION_WEBHOOK_SECRET_REF));assertFalse(ConfigurationPolicy.secret(Key.NOTIFICATION_SENDER));
    }
    @Test void mockProvidersAreRejectedInProductionButAllowedElsewhere(){
        assertThrows(ConfigurationPolicy.Rejected.class,()->ConfigurationPolicy.validate(Key.SUBSCRIPTION_PAYMENT_PROVIDER,"MOCK",true));
        assertThrows(ConfigurationPolicy.Rejected.class,()->ConfigurationPolicy.validate(Key.SUBSCRIPTION_PAYMENT_PROVIDER,"MOCK_GATEWAY",true));
        assertEquals("MOCK",ConfigurationPolicy.validate(Key.SUBSCRIPTION_PAYMENT_PROVIDER,"MOCK",false));
        assertEquals("ACME_PAY",ConfigurationPolicy.validate(Key.SUBSCRIPTION_PAYMENT_PROVIDER,"ACME_PAY",true));
        assertThrows(ConfigurationPolicy.Rejected.class,()->ConfigurationPolicy.validate(Key.SUBSCRIPTION_PAYMENT_PROVIDER,"acme pay",false));
    }
    @Test void keysAreResolvedByIdAndBlankValuesRejected(){
        assertEquals(Key.NOTIFICATION_SENDER,ConfigurationPolicy.Key.find("platform.notification.sender").orElseThrow());assertTrue(ConfigurationPolicy.Key.find("vendor.payment.key").isEmpty());
        assertThrows(ConfigurationPolicy.Rejected.class,()->ConfigurationPolicy.validate(Key.NOTIFICATION_SENDER," ",false));assertThrows(ConfigurationPolicy.Rejected.class,()->ConfigurationPolicy.validate(Key.NOTIFICATION_SENDER,"a\nb",false));
        for(var key:Key.values())assertTrue(key.id().startsWith("platform."),"platform namespace only: "+key);
    }
}
