package com.saumya.pos;

import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.checkout.CheckoutService;
import com.saumya.pos.checkout.DraftRecoveryPolicy;
import com.saumya.pos.checkout.internal.DraftLineRepository;
import com.saumya.pos.checkout.internal.DraftRepository;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.Pricing;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutServiceAuthorizationBoundaryTest {
    private record Fixture(
        CheckoutService service,
        DraftRepository orders,
        DraftLineRepository lines,
        CartService carts,
        CustomerAccess customers,
        CatalogService catalogue,
        InventoryService stock,
        Pricing pricing,
        ApplicationEventPublisher events
    ) {}

    private Fixture fixture(AuthorizationContext.Context context) {
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(context);
        return fixture(authorization);
    }

    private Fixture fixture(AuthorizationContext authorization) {
        var orders = mock(DraftRepository.class);
        var lines = mock(DraftLineRepository.class);
        var carts = mock(CartService.class);
        var customers = mock(CustomerAccess.class);
        var catalogue = mock(CatalogService.class);
        var stock = mock(InventoryService.class);
        var pricing = mock(Pricing.class);
        var events = mock(ApplicationEventPublisher.class);
        var retail = new PosService(null, null, authorization);
        var service = new CheckoutService(
            orders, lines, carts, customers, catalogue, stock, retail, pricing,
            Clock.systemUTC(), new DraftRecoveryPolicy(), 600
        );
        ReflectionTestUtils.setField(service, "events", events);
        return new Fixture(service, orders, lines, carts, customers, catalogue, stock, pricing, events);
    }

    private static AuthorizationContext.Context vendor(Set<String> permissions) {
        return new AuthorizationContext.Context("cashier", 7L, 12L, permissions, false, false, true);
    }

    private static CheckoutService.PosDraft draft() {
        return new CheckoutService.PosDraft(
            "session-1", List.of(new CheckoutService.Item(101L, 1)), false, null, null
        );
    }

    private static CheckoutService.CashSale cashSale() {
        return new CheckoutService.CashSale(
            "session-1", List.of(new CheckoutService.Item(101L, 1)),
            new BigDecimal("100.00"), false, null, null
        );
    }

    @Test
    void viewOnlyVendorCannotInvokeAnyCheckoutMutationDirectly() {
        var f = fixture(vendor(Set.of("POS_VIEW")));

        forbidden(() -> f.service().pos("cashier", "direct-pos-key-01", draft()));
        forbidden(() -> f.service().cashSale("cashier", "direct-cash-key-01", cashSale()));
        forbidden(() -> f.service().cancelPos("cashier", "order-1", "Cancel request"));
        forbidden(() -> f.service().finalizeCash("cashier", "order-1", new BigDecimal("100.00")));

        verifyNoInteractions(
            f.orders(), f.lines(), f.carts(), f.customers(), f.catalogue(),
            f.stock(), f.pricing(), f.events()
        );
    }

    @Test
    void customerCheckoutCommandsRejectVendorActorBeforeCustomerOrOrderAccess() {
        var f = fixture(vendor(Set.of("POS_VIEW", "POS_SALE_CREATE")));

        forbidden(() -> f.service().online("cashier", "direct-online-key-01",
            new CheckoutService.OnlineDraft("cart-1")));
        forbidden(() -> f.service().onlineList("cashier"));
        forbidden(() -> f.service().cancelOnline("cashier", "order-1", "Cancel request"));

        verifyNoInteractions(
            f.orders(), f.lines(), f.carts(), f.customers(), f.catalogue(),
            f.stock(), f.pricing(), f.events()
        );
    }

    @Test
    void platformAndCustomerActorsCannotEnterVendorCheckoutBoundaries() {
        var customer = fixture(new AuthorizationContext.Context(
            "buyer", null, null, Set.of("CUSTOMER"), false, true, false
        ));
        forbidden(() -> customer.service().pos("buyer", "customer-pos-key-01", draft()));
        forbidden(() -> customer.service().cashSale("buyer", "customer-cash-key-01", cashSale()));
        forbidden(() -> customer.service().posList("buyer", "session-1"));
        verifyNoInteractions(
            customer.orders(), customer.lines(), customer.carts(), customer.customers(),
            customer.catalogue(), customer.stock(), customer.pricing(), customer.events()
        );

        var platform = fixture(new AuthorizationContext.Context(
            "platform-admin", null, null, Set.of("PLATFORM_TENANT_VIEW"), true, false, false
        ));
        forbidden(() -> platform.service().pos("platform-admin", "platform-pos-key-01", draft()));
        forbidden(() -> platform.service().cashSale("platform-admin", "platform-cash-key-01", cashSale()));
        forbidden(() -> platform.service().posList("platform-admin", "session-1"));
        verifyNoInteractions(
            platform.orders(), platform.lines(), platform.carts(), platform.customers(),
            platform.catalogue(), platform.stock(), platform.pricing(), platform.events()
        );
    }

    @Test
    void anonymousDirectCheckoutCallsFailBeforeAnyCollaborator() {
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenThrow(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
        );
        var f = fixture(authorization);

        unauthorized(() -> f.service().pos("cashier", "anonymous-pos-key-01", draft()));
        unauthorized(() -> f.service().online("buyer", "anonymous-online-key-01",
            new CheckoutService.OnlineDraft("cart-1")));

        verifyNoInteractions(
            f.orders(), f.lines(), f.carts(), f.customers(), f.catalogue(),
            f.stock(), f.pricing(), f.events()
        );
    }

    private static void forbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void unauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
