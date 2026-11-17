package com.saumya.pos;

import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.checkout.CheckoutService;
import com.saumya.pos.checkout.DraftRecoveryPolicy;
import com.saumya.pos.checkout.DraftExpiryJob;
import com.saumya.pos.checkout.internal.DraftOrder;
import com.saumya.pos.checkout.internal.DraftLineRepository;
import com.saumya.pos.checkout.internal.DraftRepository;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.inventory.internal.InventoryIssueRepository;
import com.saumya.pos.inventory.internal.InventoryRepository;
import com.saumya.pos.inventory.internal.ReservationRepository;
import com.saumya.pos.inventory.internal.ReturnDispositionRepository;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.shared.Pricing;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class DraftExpiryAuthorizationTest {
    private static final String SYSTEM_GRANT = "SYSTEM_DRAFT_EXPIRY";

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority(SYSTEM_GRANT))));
    }

    @Test
    void inventoryReleaseRejectsSpoofedSystemGrantBeforeOrderLookupOrReservationAccess() {
        var jdbc = mock(JdbcTemplate.class);
        var reservations = mock(ReservationRepository.class);
        var service = new InventoryService(
            mock(InventoryRepository.class), reservations,
            mock(ReturnDispositionRepository.class), mock(InventoryIssueRepository.class),
            mock(ApplicationEventPublisher.class), null, null, jdbc);

        authenticate("attacker");

        assertThatThrownBy(() -> service.release(7L, "draft-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Trusted draft-expiry worker required");

        verifyNoInteractions(jdbc, reservations);
    }

    @Test
    void checkoutExpiryRejectsSpoofedSystemGrantBeforeDraftRepositoryAccess() {
        var orders = mock(DraftRepository.class);
        var service = new CheckoutService(
            orders, mock(DraftLineRepository.class), mock(CartService.class),
            mock(CustomerAccess.class), mock(CatalogService.class), mock(InventoryService.class),
            mock(PosService.class), mock(Pricing.class), Clock.systemUTC(),
            mock(DraftRecoveryPolicy.class), 600);

        authenticate("attacker");

        assertThatThrownBy(() -> service.expire(7L, "draft-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Trusted draft-expiry worker required");

        verifyNoInteractions(orders);
    }
}
