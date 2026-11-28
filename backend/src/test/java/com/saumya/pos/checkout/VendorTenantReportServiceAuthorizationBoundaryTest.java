package com.saumya.pos.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saumya.pos.checkout.internal.SalesAggregate;
import com.saumya.pos.checkout.internal.SalesLineAggregate;
import com.saumya.pos.checkout.internal.VendorTenantAnalyticsRepository;
import com.saumya.pos.checkout.internal.VendorTenantOutletAggregate;
import com.saumya.pos.checkout.internal.VendorTenantProductAggregate;
import com.saumya.pos.checkout.internal.VendorTenantTenderAggregate;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.server.ResponseStatusException;

class VendorTenantReportServiceAuthorizationBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-11-28T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 28);

    @Test void nonVendorActorCannotQueryTenantReportingData() {
        var repository = mock(VendorTenantAnalyticsRepository.class);
        var auth = mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "customer@example.test", null, null, null, Set.of("CUSTOMER"), false, true, false));
        var service = new VendorTenantReportService(repository, auth, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.report(null, null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test void vendorWithoutReportPermissionCannotQueryTenantReportingData() {
        var repository = mock(VendorTenantAnalyticsRepository.class);
        var auth = mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "inventory@example.test", 71L, 5L, "71:inventory@example.test",
            Set.of("INVENTORY_VIEW"), false, false, true));
        var service = new VendorTenantReportService(repository, auth, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.report(null, null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test void reportUsesTenantFromMembershipAndAggregatesIndependentSources() {
        var repository = mock(VendorTenantAnalyticsRepository.class);
        var auth = mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "admin@example.test", 71L, 5L, "71:admin@example.test",
            Set.of("POS_REPORT_VIEW"), false, false, true));
        when(repository.ordersForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"))).thenReturn(List.of(
                new SalesAggregate(TODAY, "ONLINE", "PAID", 2, new BigDecimal("1200"),
                    BigDecimal.ZERO, BigDecimal.ZERO, 2),
                new SalesAggregate(TODAY, "IN_STORE", "PAID", 1, new BigDecimal("300"),
                    new BigDecimal("300"), BigDecimal.ZERO, 1)));
        when(repository.linesForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"))).thenReturn(List.of(
                new SalesLineAggregate(TODAY, "ONLINE", new BigDecimal("200"), 4, new BigDecimal("20")),
                new SalesLineAggregate(TODAY, "IN_STORE", new BigDecimal("50"), 1, BigDecimal.ZERO)));
        when(repository.distinctPaidCustomersForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"))).thenReturn(2L);
        when(repository.outletsForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"))).thenReturn(List.of(
                new VendorTenantOutletAggregate("Store One", "IN_STORE", 1, 1,
                    new BigDecimal("300"), 0, BigDecimal.ZERO)));
        when(repository.topProductsForTenant(eq(71L), any(Instant.class), any(Instant.class), any()))
            .thenReturn(new PageImpl<>(List.of(new VendorTenantProductAggregate(9L, "Solar Panel", 5,
                new BigDecimal("1500"), new BigDecimal("20")))));
        when(repository.confirmedTendersForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"))).thenReturn(List.of(
                new VendorTenantTenderAggregate("ONLINE", "UPI", "ONLINE_MOCK", 2, new BigDecimal("1200"))));

        var service = new VendorTenantReportService(repository, auth, Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.report(TODAY, TODAY);

        assertThat(result.totals().paidOrders()).isEqualTo(3);
        assertThat(result.totals().sales()).isEqualByComparingTo("1500");
        assertThat(result.totals().tax()).isEqualByComparingTo("250");
        assertThat(result.totals().beforeTax()).isEqualByComparingTo("1250");
        assertThat(result.totals().units()).isEqualTo(5);
        assertThat(result.totals().distinctCustomers()).isEqualTo(2);
        assertThat(result.channels()).hasSize(2);
        assertThat(result.outlets()).hasSize(1);
        assertThat(result.topProducts()).hasSize(1);
        assertThat(result.confirmedTenders()).hasSize(1);
        verify(repository).ordersForTenant(eq(71L), any(Instant.class), any(Instant.class));
    }

    @Test void invalidRangeIsRejectedBeforeAnyRepositoryReads() {
        var repository = mock(VendorTenantAnalyticsRepository.class);
        var auth = mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "admin@example.test", 71L, 5L, "71:admin@example.test",
            Set.of("POS_REPORT_VIEW"), false, false, true));
        var service = new VendorTenantReportService(repository, auth, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.report(TODAY, TODAY.minusDays(1))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.report(TODAY.minusDays(366), TODAY)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
