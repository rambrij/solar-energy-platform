package com.saumya.pos;

import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.checkout.CheckoutService;
import com.saumya.pos.checkout.DraftExpiryJob;
import com.saumya.pos.checkout.DraftRecoveryPolicy;
import com.saumya.pos.checkout.internal.DraftLineRepository;
import com.saumya.pos.checkout.internal.DraftOrder;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    void scheduledWorkerInstallsDedicatedIdentityOnlyForTheExpiryCallAndRestoresCallerContext() {
        var orders = mock(DraftRepository.class);
        var checkout = mock(CheckoutService.class);
        var previous = UsernamePasswordAuthenticationToken.authenticated(
            "operator", null, List.of(new SimpleGrantedAuthority("POS_VIEW")));
        SecurityContextHolder.getContext().setAuthentication(previous);
        var job = new DraftExpiryJob(orders, checkout, Clock.systemUTC(), true);

        doAnswer(invocation -> {
            var worker = SecurityContextHolder.getContext().getAuthentication();
            assertThat(worker.getName()).isEqualTo("system:draft-expiry");
            assertThat(worker.getAuthorities()).extracting("authority").containsExactly(SYSTEM_GRANT);
            return null;
        }).when(checkout).expire(7L, "draft-1");

        job.expireOne(7L, "draft-1");

        verify(checkout).expire(7L, "draft-1");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    @Test
    void unauthorisedInventoryOperationsAreDeniedBeforeAnyPersistenceOrEventCollaborator() {
        var balances = mock(InventoryRepository.class);
        var reservations = mock(ReservationRepository.class);
        var returns = mock(ReturnDispositionRepository.class);
        var issues = mock(InventoryIssueRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var auth = mock(com.saumya.pos.security.AuthorizationContext.class);
        var customers = mock(CustomerAccess.class);
        var jdbc = mock(JdbcTemplate.class);
        when(auth.current()).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No inventory actor"));
        var service = new InventoryService(balances, reservations, returns, issues, events, auth, customers, jdbc);

        assertDenied(() -> service.available(7L, 11L));
        assertDenied(() -> service.atBranch(7L, 9L, 11L));
        assertDenied(() -> service.atBranchForPos(7L, 9L, 11L));
        assertDenied(() -> service.issue(7L, 9L, 11L, 1));
        assertDenied(() -> service.receive(7L, 9L, 11L, 1));
        assertDenied(() -> service.reserve(7L, 9L, 11L, 1, "draft-1", java.time.Instant.now()));
        assertDenied(() -> service.release(7L, "draft-1"));
        assertDenied(() -> service.consume(7L, "draft-1"));
        assertDenied(() -> service.inspectReturn(7L, 9L, "return-1", 11L, 1,
            "RESTOCK", "SELLABLE", "attacker", java.time.Instant.now()));

        verifyNoInteractions(balances, reservations, returns, issues, events, customers, jdbc);
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }
}
