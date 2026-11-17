package com.saumya.pos;

import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.identity.internal.CustomerProfile;
import com.saumya.pos.identity.internal.CustomerRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerAccessAuthorizationBoundaryTest {
    private record Fixture(CustomerAccess service, CustomerRepository customers) {}

    private Fixture fixture(AuthorizationContext.Context actor) {
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(actor);
        return fixture(authorization);
    }

    private Fixture fixture(AuthorizationContext authorization) {
        var customers = mock(CustomerRepository.class);
        return new Fixture(new CustomerAccess(customers, authorization), customers);
    }

    @Test
    void vendorAndPlatformActorsCannotUseCustomerIdentityResolver() {
        var vendor = fixture(new AuthorizationContext.Context(
            "cashier", 7L, 12L, Set.of("POS_VIEW"), false, false, true
        ));
        forbidden(() -> vendor.service().resolve("buyer"));
        forbidden(() -> vendor.service().lock(100L));
        verifyNoInteractions(vendor.customers());

        var platform = fixture(new AuthorizationContext.Context(
            "platform-admin", null, null, Set.of("PLATFORM_TENANT_VIEW"), true, false, false
        ));
        forbidden(() -> platform.service().resolve("buyer"));
        forbidden(() -> platform.service().lock(100L));
        verifyNoInteractions(platform.customers());
    }

    @Test
    void customerCannotResolveAnotherUsername() {
        var f = fixture(new AuthorizationContext.Context(
            "buyer-a", null, null, Set.of("CUSTOMER"), false, true, false
        ));

        forbidden(() -> f.service().resolve("buyer-b"));
        verifyNoInteractions(f.customers());
    }

    @Test
    void customerCannotLockAnotherCustomersProfile() {
        var f = fixture(new AuthorizationContext.Context(
            "buyer-b", null, null, Set.of("CUSTOMER"), false, true, false
        ));
        when(f.customers().resolve("buyer-b")).thenReturn(Optional.of(200L));

        assertThatThrownBy(() -> f.service().lock(100L))
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(f.customers()).resolve("buyer-b");
        verify(f.customers(), never()).lock(anyLong());
    }

    @Test
    void customerCanResolveAndLockOnlyTheirOwnProfile() {
        var f = fixture(new AuthorizationContext.Context(
            "buyer-a", null, null, Set.of("CUSTOMER"), false, true, false
        ));
        when(f.customers().resolve("buyer-a")).thenReturn(Optional.of(100L));
        when(f.customers().lock(100L)).thenReturn(Optional.of(new CustomerProfile(100L, 77L)));

        assertThat(f.service().resolve("buyer-a")).isEqualTo(100L);
        f.service().lock(100L);

        verify(f.customers()).resolve("buyer-a");
        verify(f.customers()).lock(100L);
        verifyNoMoreInteractions(f.customers());
    }

    private static void forbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
