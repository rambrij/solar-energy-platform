package com.saumya.pos.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.subscription.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantSubscriptionServiceAuthorizationTest {
    @Test
    void subscriptionWritesAndReadsRejectVendorBeforeAnyPersistenceOrTenantLookup() {
        var subscriptions=mock(TenantSubscriptionRepository.class);
        var plans=mock(SubscriptionPlanRepository.class);
        var versions=mock(SubscriptionPlanVersionRepository.class);
        var events=mock(SubscriptionEventRepository.class);
        var invoices=mock(SubscriptionInvoiceRepository.class);
        var payments=mock(SubscriptionPaymentRepository.class);
        var tenants=mock(TenantLifecycleService.class);
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "vendor",7L,12L,java.util.Set.of("POS_VIEW"),false,false,true));
        var service=new TenantSubscriptionServiceImpl(subscriptions,plans,versions,events,invoices,
            payments,tenants,new ObjectMapper(),Clock.systemUTC());
        ReflectionTestUtils.setField(service,"authorization",authorization);

        denied(()->service.create(null,"vendor"));
        denied(()->service.transition(1L,"CANCELLED","reason","vendor"));
        denied(()->service.forTenant(7L));
        denied(service::all);
        denied(()->service.events(1L));
        denied(()->service.billingHistory(1L));

        verifyNoInteractions(subscriptions,plans,versions,events,invoices,payments,tenants);
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }
}
