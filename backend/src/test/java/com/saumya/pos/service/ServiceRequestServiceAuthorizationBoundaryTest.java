package com.saumya.pos.service;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServiceRequestServiceAuthorizationBoundaryTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final AuthorizationContext authorization=mock(AuthorizationContext.class);
    private final OrderHistoryService orders=mock(OrderHistoryService.class);
    private final EventOutbox outbox=mock(EventOutbox.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"),ZoneOffset.UTC);

    private ServiceRequestService service() {
        return new ServiceRequestService(db,authorization,orders,outbox,clock);
    }

    private void vendorContext(Set<String> permissions) {
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "engineer@vendor.test",7L,2L,"7:engineer",permissions,false,false,true));
    }

    @Test
    void unrelatedShipmentGrantCannotReadOrMutateServiceRequests() {
        vendorContext(Set.of("SHIPMENT_VIEW","SHIPMENT_UPDATE"));
        var service=service();
        assertThatThrownBy(service::vendorList).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.vendorGet(10)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(service::eligibleVendorOrders).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.createForVendor(new ServiceRequestService.Create(
            "order-1","Issue","Details","NORMAL"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.assign(10,new ServiceRequestService.Assign(
            0,"engineer@vendor.test","Reason"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.updateStatus(10,new ServiceRequestService.StatusChange(
            0,"IN_PROGRESS","Started",true))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,orders,outbox);
    }

    @Test
    void customerListQueriesOnlyOrdersResolvedForTheAuthenticatedCustomer() {
        var reference=new OrderHistoryService.CustomerOrderReference("owned-order",7L,"Vendor A");
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of(reference));
        when(db.queryForList(contains("FROM service_requests WHERE tenant_id=? AND order_id=?"),
            eq(7L),eq("owned-order"))).thenReturn(List.of(Map.of(
                "id",17L,"order_id","owned-order","subject","Inverter issue",
                "description","Customer reports shutdown","priority","HIGH","status","OPEN",
                "created_at",java.sql.Timestamp.from(clock.instant()),
                "updated_at",java.sql.Timestamp.from(clock.instant()),
                "resolved_at",null)));
        var rows=service().customerList("customer@example.test");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().orderId()).isEqualTo("owned-order");
        verify(orders).customerPaidOnlineOrderReferences("customer@example.test");
        verify(db).queryForList(contains("WHERE tenant_id=? AND order_id=?"),eq(7L),eq("owned-order"));
    }
}
