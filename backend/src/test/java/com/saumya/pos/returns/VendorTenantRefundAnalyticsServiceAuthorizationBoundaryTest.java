package com.saumya.pos.returns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saumya.pos.returns.internal.RefundReportRepository;
import com.saumya.pos.returns.internal.VendorTenantRefundHeaderAggregate;
import com.saumya.pos.returns.internal.VendorTenantRefundLineAggregate;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class VendorTenantRefundAnalyticsServiceAuthorizationBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-11-28T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 28);

    @Test void vendorWithoutRefundPermissionIsRejectedBeforeReads() {
        var repository = mock(RefundReportRepository.class);
        var auth = mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "sales@example.test", 71L, 5L, "71:sales@example.test",
            Set.of("POS_REPORT_VIEW"), false, false, true));
        var service = new VendorTenantRefundAnalyticsService(repository, auth, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.report(null, null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test void refundReportUsesMembershipTenantAndAggregatesHeaderAndLineSourcesSeparately() {
        var repository = mock(RefundReportRepository.class);
        var auth = mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "accountant@example.test", 71L, 5L, "71:accountant@example.test",
            Set.of("POS_REPORT_VIEW", "POS_REFUND_VIEW"), false, false, true));
        when(repository.headersForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"))).thenReturn(List.of(
                new VendorTenantRefundHeaderAggregate(TODAY, 2, new BigDecimal("132"))));
        when(repository.linesForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"))).thenReturn(List.of(
                new VendorTenantRefundLineAggregate(TODAY, new BigDecimal("120"),
                    new BigDecimal("12"), 7)));

        var service = new VendorTenantRefundAnalyticsService(repository, auth, Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.report(TODAY, TODAY);

        assertThat(result.totals().refunds()).isEqualTo(2);
        assertThat(result.totals().cash()).isEqualByComparingTo("132");
        assertThat(result.totals().beforeTax()).isEqualByComparingTo("120");
        assertThat(result.totals().tax()).isEqualByComparingTo("12");
        assertThat(result.totals().units()).isEqualTo(7);
        assertThat(result.days()).hasSize(1);
        verify(repository).headersForTenant(71L, Instant.parse("2026-11-28T00:00:00Z"),
            Instant.parse("2026-11-29T00:00:00Z"));
    }
}
