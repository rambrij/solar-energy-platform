package com.saumya.pos.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.subscription.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
        when(authorization.current()).thenReturn(new AuthorizationContext.Context("vendor", 7L, 12L, String.valueOf(7L) + ":" + ("vendor"), java.util.Set.of("POS_VIEW"), false, false, true));
        var service=new TenantSubscriptionServiceImpl(subscriptions,plans,versions,events,invoices,
            payments,tenants,new ObjectMapper(),Clock.systemUTC());
        ReflectionTestUtils.setField(service,"authorization",authorization);

        denied(()->service.create(null,"vendor"));
        denied(()->service.transition(1L,"CANCELLED","reason","vendor",0L));
        denied(()->service.forTenant(7L));
        denied(service::all);
        denied(()->service.events(1L));
        denied(()->service.billingHistory(1L));

        verifyNoInteractions(subscriptions,plans,versions,events,invoices,payments,tenants);
    }

    @Test
    void subscriptionMetricsRequirePlatformAnalyticsBeforeReadingAggregateCounts() {
        var subscriptions=mock(TenantSubscriptionRepository.class);
        var service=new TenantSubscriptionServiceImpl(subscriptions,null,null,null,null,null,null,null,Clock.systemUTC());
        try {
            for (String actor : java.util.List.of(AuthorizationContext.ACTOR_CONTEXT_VENDOR,AuthorizationContext.ACTOR_CONTEXT_PLATFORM)) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "operator","n/a",java.util.List.of(new SimpleGrantedAuthority(actor),
                    new SimpleGrantedAuthority("PLATFORM_SUBSCRIPTION_VIEW"))));
                denied(service::statusCounts);
            }
            verifyNoInteractions(subscriptions);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void staleSubscriptionVersionCannotChangeStatusOrWriteHistory() {
        var subscriptions=mock(TenantSubscriptionRepository.class);
        var subscription=mock(TenantSubscription.class);
        var events=mock(SubscriptionEventRepository.class);
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context("platform",null,null,null,
            java.util.Set.of("PLATFORM_SUBSCRIPTION_ADJUST"),true,false,false));
        when(subscriptions.findById(1L)).thenReturn(java.util.Optional.of(subscription));
        when(subscription.version()).thenReturn(3L);
        var service=new TenantSubscriptionServiceImpl(subscriptions,null,null,events,null,null,null,null,Clock.systemUTC());
        ReflectionTestUtils.setField(service,"authorization",authorization);

        assertThatThrownBy(()->service.transition(1L,"SUSPENDED","reason","platform",2L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.CONFLICT);
        verify(subscription).version();
        verifyNoMoreInteractions(subscription);
        verifyNoInteractions(events);
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }
}
