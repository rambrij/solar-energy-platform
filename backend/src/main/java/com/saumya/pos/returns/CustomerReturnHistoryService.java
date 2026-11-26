package com.saumya.pos.returns;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.returns.internal.CashRefund;
import com.saumya.pos.returns.internal.CashRefundRepository;
import com.saumya.pos.returns.internal.ReturnCase;
import com.saumya.pos.returns.internal.ReturnItemRepository;
import com.saumya.pos.returns.internal.ReturnRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Customer-safe read projection for existing POS-originated returns. */
@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class CustomerReturnHistoryService {
    private final OrderHistoryService orders;
    private final ReturnRepository returns;
    private final ReturnItemRepository items;
    private final CashRefundRepository refunds;

    public CustomerReturnHistoryService(OrderHistoryService orders,
                                        ReturnRepository returns,
                                        ReturnItemRepository items,
                                        CashRefundRepository refunds) {
        this.orders=orders;
        this.returns=returns;
        this.items=items;
        this.refunds=refunds;
    }

    public record CustomerItem(String name, int quantity) {}
    public record ReturnStatus(
        String id,
        String state,
        java.time.Instant requestedAt,
        java.time.Instant reviewedAt,
        java.time.Instant inspectedAt,
        String cashRefundStatus,
        BigDecimal cashRefundAmount,
        java.time.Instant cashRefundedAt,
        List<CustomerItem> items
    ) {}
    public record History(String orderId, List<ReturnStatus> returns) {}

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order unavailable");
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    public History history(String username, String orderId) {
        if (orderId == null || !orderId.matches("[A-Za-z0-9-]{1,36}")) throw missing();

        // Authenticate and resolve the customer-owned order before deriving tenant scope.
        var owned = orders.customerOrderAccess(username, orderId);
        // The implemented POS return workflow covers paid IN_STORE orders only.
        if (!"IN_STORE".equals(owned.channel()) || !"PAID".equals(owned.status())) {
            return new History(orderId, List.of());
        }

        // Bound both the parent projection and the child lookup to the owned order.
        var cases = returns.findTop20ByTenantAndOrderIdOrderByCreatedDescIdDesc(owned.tenantId(), orderId);
        if (cases.isEmpty()) return new History(orderId, List.of());

        var returnIds = cases.stream().map(ReturnCase::id).toList();
        var refundByReturn = new HashMap<String, CashRefund>();
        for (CashRefund refund : refunds.findAllByTenantAndReturnIdIn(owned.tenantId(), returnIds)) {
            refundByReturn.putIfAbsent(refund.returned(), refund);
        }

        var result = cases.stream()
            .map(returned -> project(owned.tenantId(), returned, refundByReturn.get(returned.id())))
            .toList();
        return new History(orderId, result);
    }

    private ReturnStatus project(long tenant, ReturnCase returned, CashRefund refund) {
        var customerItems = items.customerItems(tenant, returned.id()).stream()
            .map(item -> new CustomerItem(item.name(), item.quantity()))
            .toList();
        return new ReturnStatus(
            returned.id(), returned.state(), returned.created(), returned.reviewedAt(), returned.inspectedAt(),
            refund == null ? "NOT_INITIATED" : "PAID",
            refund == null ? null : refund.amount(),
            refund == null ? null : refund.paid(),
            customerItems
        );
    }
}
