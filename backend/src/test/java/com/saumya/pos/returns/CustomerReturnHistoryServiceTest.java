package com.saumya.pos.returns;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.returns.internal.CashRefund;
import com.saumya.pos.returns.internal.CashRefundRepository;
import com.saumya.pos.returns.internal.ReturnCase;
import com.saumya.pos.returns.internal.ReturnItem;
import com.saumya.pos.returns.internal.ReturnItemRepository;
import com.saumya.pos.returns.internal.ReturnRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerReturnHistoryServiceTest {
    @Mock OrderHistoryService orders;
    @Mock ReturnRepository returns;
    @Mock ReturnItemRepository items;
    @Mock CashRefundRepository refunds;
    CustomerReturnHistoryService service;

    @BeforeEach void setUp() {
        service = new CustomerReturnHistoryService(orders, returns, items, refunds);
    }

    private OrderHistoryService.Detail ownedOrder(String id, String channel, String status) {
        Instant now = Instant.parse("2026-11-01T08:00:00Z");
        var order = new OrderHistoryService.Order(id, "Saumya Solar", channel, status,
            now, now.plusSeconds(3600), BigDecimal.ZERO, null, false, "CONFIRMED");
        return new OrderHistoryService.Detail(order, List.of(), null, null, null, now);
    }

    @Test void returnsOnlyCustomerSafeStatusForAnOwnedPaidInStoreOrder() {
        Instant requested = Instant.parse("2026-11-01T08:00:00Z");
        Instant reviewed = Instant.parse("2026-11-01T09:00:00Z");
        Instant inspected = Instant.parse("2026-11-01T10:00:00Z");
        Instant refundedAt = Instant.parse("2026-11-01T11:00:00Z");
        var returned = new ReturnCase("return-1", 17, 2, "owned-order", "private-cashier", "private-session",
            "key", "hash", "private reason", "ORIGINAL_RECEIPT", "private proof note", requested);
        returned.review("APPROVED", "private-reviewer", "private review reason", reviewed);
        returned.inspect("private-inspector", "inspection-hash", inspected);
        var item = new ReturnItem(17, "return-1", "owned-order", 9, "Saved solar panel", 2);
        var refund = new CashRefund("refund-1", 17, 2, "return-1", "owned-order", 44,
            "private-cashier", "private-requester", "private-session", "refund-key", "request-hash",
            "quote-hash", new BigDecimal("75.00"), "private cash handover reason", refundedAt);

        when(orders.customerDetail("buyer@example.test", "owned-order"))
            .thenReturn(ownedOrder("owned-order", "IN_STORE", "PAID"));
        when(orders.customerOrderTenant("buyer@example.test", "owned-order")).thenReturn(17L);
        when(returns.findTop20ByTenantAndOrderIdOrderByCreatedDescIdDesc(17, "owned-order"))
            .thenReturn(List.of(returned));
        when(refunds.findAllByTenantAndReturnIdIn(17, List.of("return-1")))
            .thenReturn(List.of(refund));
        when(items.customerItems(17, "return-1")).thenReturn(List.of(item));

        var result = service.history("buyer@example.test", "owned-order");

        assertThat(result.orderId()).isEqualTo("owned-order");
        assertThat(result.returns()).hasSize(1);
        var status = result.returns().getFirst();
        assertThat(status.state()).isEqualTo("INSPECTED");
        assertThat(status.requestedAt()).isEqualTo(requested);
        assertThat(status.reviewedAt()).isEqualTo(reviewed);
        assertThat(status.inspectedAt()).isEqualTo(inspected);
        assertThat(status.cashRefundStatus()).isEqualTo("PAID");
        assertThat(status.cashRefundAmount()).isEqualByComparingTo("75.00");
        assertThat(status.cashRefundedAt()).isEqualTo(refundedAt);
        assertThat(status.items()).containsExactly(new CustomerReturnHistoryService.CustomerItem("Saved solar panel", 2));
        assertThat(status.toString()).doesNotContain("private-cashier", "private-reviewer", "private-inspector",
            "private-session", "private reason", "private proof note", "private review reason", "private cash handover reason");
    }

    @Test void onlineOrdersDoNotQueryPosReturnOrCashRefundRecords() {
        when(orders.customerDetail("buyer@example.test", "online-order"))
            .thenReturn(ownedOrder("online-order", "ONLINE", "PAID"));
        var result = service.history("buyer@example.test", "online-order");
        assertThat(result.returns()).isEmpty();
        verifyNoInteractions(returns, items, refunds);
    }

    @Test void unpaidInStoreOrdersDoNotQueryPosReturnOrCashRefundRecords() {
        when(orders.customerDetail("buyer@example.test", "draft-order"))
            .thenReturn(ownedOrder("draft-order", "IN_STORE", "DRAFT"));
        var result = service.history("buyer@example.test", "draft-order");
        assertThat(result.returns()).isEmpty();
        verifyNoInteractions(returns, items, refunds);
    }

    @Test void foreignOrderRejectionStopsBeforeReturnRepositories() {
        when(orders.customerDetail("buyer@example.test", "foreign-order"))
            .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order unavailable"));
        assertThatThrownBy(() -> service.history("buyer@example.test", "foreign-order"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(returns, items, refunds);
    }

    @Test void invalidOrderReferenceStopsBeforeOwnershipOrPersistenceLookup() {
        assertThatThrownBy(() -> service.history("buyer@example.test", "../foreign"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(orders, returns, items, refunds);
    }
}
