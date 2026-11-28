package com.saumya.pos.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saumya.pos.checkout.internal.VendorInvoiceRegisterRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class VendorInvoiceRegisterServiceAuthorizationBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-11-28T00:00:00Z");

    @Test void customerIsRejectedBeforeInvoicePersistenceIsQueried() {
        var repository = mock(VendorInvoiceRegisterRepository.class);
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "customer@example.test", null, null, null, Set.of("CUSTOMER"), false, true, false));
        var service = new VendorInvoiceRegisterService(repository, authorization,
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.list(null, null, "ALL", 0, 50))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(repository);
    }

    @Test void vendorWithoutInvoiceViewIsRejectedBeforeQuery() {
        var repository = mock(VendorInvoiceRegisterRepository.class);
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "employee@vendor.test", 71L, 5L, "71:employee@vendor.test",
            Set.of("POS_VIEW", "POS_REPORT_VIEW"), false, false, true));
        var service = new VendorInvoiceRegisterService(repository, authorization,
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.list(null, null, "ALL", 0, 50))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(repository);
    }

    @Test void listUsesAuthenticatedTenantAndReturnsOnlyInvoiceRegisterProjection() {
        var repository = mock(VendorInvoiceRegisterRepository.class);
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "accountant@vendor.test", 71L, 5L, "71:accountant@vendor.test",
            Set.of("INVOICE_VIEW"), false, false, true));
        when(repository.totals(eq(71L), any(Timestamp.class), any(Timestamp.class), eq("ONLINE")))
            .thenReturn(new Object[]{1L, new BigDecimal("110.00"), new BigDecimal("110.00"), 1L, 0L});
        when(repository.page(eq(71L), any(Timestamp.class), any(Timestamp.class),
            eq("ONLINE"), eq(10), eq(0))).thenReturn(List.<Object[]>of(new Object[]{
                "order-1", "INV-1", "Vendor A", "Online", new BigDecimal("110.00"),
                Timestamp.from(NOW), "ONLINE", "MOCK_UPI", "pay-1", new BigDecimal("110.00")
            }));
        var service = new VendorInvoiceRegisterService(repository, authorization,
            Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.list(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 28),
            "online", 0, 10);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().invoiceNumber()).isEqualTo("INV-1");
        assertThat(result.totals().invoicedAmount()).isEqualByComparingTo("110.00");
        assertThat(result.totals().confirmedPayments()).isEqualByComparingTo("110.00");
        verify(repository).totals(eq(71L), any(Timestamp.class), any(Timestamp.class), eq("ONLINE"));
        verify(repository).page(eq(71L), any(Timestamp.class), any(Timestamp.class), eq("ONLINE"), eq(10), eq(0));
    }

    @Test void rejectsInvalidRangeAndPageBeforeRepositoryAccess() {
        var repository = mock(VendorInvoiceRegisterRepository.class);
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "accountant@vendor.test", 71L, 5L, "71:accountant@vendor.test",
            Set.of("INVOICE_VIEW"), false, false, true));
        var service = new VendorInvoiceRegisterService(repository, authorization,
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.list(LocalDate.of(2026, 11, 29),
            LocalDate.of(2026, 11, 28), "ALL", 0, 50))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list(null, null, "ALL", -1, 50))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
