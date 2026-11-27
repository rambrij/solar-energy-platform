package com.saumya.pos.warranty;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarrantyServiceAuthorizationBoundaryTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final AuthorizationContext authorization=mock(AuthorizationContext.class);
    private final OrderHistoryService orders=mock(OrderHistoryService.class);
    private final EventOutbox outbox=mock(EventOutbox.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"),ZoneOffset.UTC);
    private WarrantyService service(){return new WarrantyService(db,authorization,orders,outbox,clock);}

    private void vendor(Set<String> grants) {
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "admin@vendor.test",7L,2L,"7:admin",grants,false,false,true));
    }

    @Test void serviceOnlyGrantCannotReadOrRegisterWarranty() {
        vendor(Set.of("SERVICE_UPDATE"));
        assertThatThrownBy(service()::vendorList).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service().register(new WarrantyService.Register(
            "order-1",9,java.time.LocalDate.now(),java.time.LocalDate.now().plusYears(1),"Terms","Reason")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,outbox,orders);
    }

    @Test void viewOnlyServiceGrantCannotDiscoverRegistrationOrdersOrItems() {
        vendor(Set.of("SERVICE_VIEW"));
        assertThatThrownBy(service()::eligibleOrders).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service().eligibleItems("order-1")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,outbox,orders);
    }

    @Test void customerCannotReadWarrantyForAnOrderTheyDoNotOwn() {
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of(
            new OrderHistoryService.CustomerOrderReference("own-order",7L,"Vendor A")));
        when(db.queryForList(contains("WHERE w.tenant_id=? AND w.order_id=? AND w.id=?"),
            eq(7L),eq("own-order"),eq(88L))).thenReturn(List.of());
        assertThatThrownBy(()->service().customerGet("customer@example.test",88))
            .isInstanceOfSatisfying(ResponseStatusException.class,error->
                assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
        verify(db).queryForList(contains("WHERE w.tenant_id=? AND w.order_id=? AND w.id=?"),
            eq(7L),eq("own-order"),eq(88L));
    }

    @Test void vendorRegistersPaidOrderItemWithImmutableAuditAndOutboxInTenantScope() {
        vendor(Set.of("SERVICE_CREATE","SERVICE_VIEW"));
        var order=new java.util.HashMap<String,Object>();
        order.put("status","PAID");order.put("sales_channel","ONLINE");order.put("customer_id",101L);
        var item=java.util.Map.<String,Object>of("name","Solar Panel");
        var saved=new java.util.HashMap<String,Object>();
        saved.put("id",55L);saved.put("order_id","order-1");saved.put("product_id",9L);
        saved.put("product_name","Solar Panel");saved.put("installation_id",null);
        saved.put("coverage_start_date",java.sql.Date.valueOf("2026-11-27"));
        saved.put("coverage_end_date",java.sql.Date.valueOf("2031-11-27"));
        saved.put("terms_summary","Five year product warranty");
        saved.put("registered_by","admin@vendor.test");
        saved.put("registered_at",java.sql.Timestamp.from(clock.instant()));

        when(db.queryForList(contains("SELECT status,sales_channel,customer_id"),any(Object[].class)))
            .thenReturn(List.of(order));
        when(db.queryForList(contains("SELECT name FROM order_items"),any(Object[].class)))
            .thenReturn(List.of(item));
        when(db.queryForList(contains("SELECT id FROM installation_jobs"),any(Object[].class)))
            .thenReturn(List.of());
        when(db.queryForObject(contains("SELECT id FROM warranty_records"),eq(Long.class),
            eq(7L),eq("order-1"),eq(9L))).thenReturn(55L);
        when(db.queryForList(contains("SELECT w.id,w.order_id"),any(Object[].class)))
            .thenReturn(List.of(saved));
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);

        var result=service().register(new WarrantyService.Register("order-1",9,
            java.time.LocalDate.parse("2026-11-27"),java.time.LocalDate.parse("2031-11-27"),
            "Five year product warranty","Manufacturer coverage confirmed"));

        assertThat(result.id()).isEqualTo(55L);
        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.productName()).isEqualTo("Solar Panel");
        assertThat(result.coverageEndDate()).isEqualTo(java.time.LocalDate.parse("2031-11-27"));
        verify(db).queryForList(contains("WHERE tenant_id=? AND id=? FOR UPDATE"),
            eq(7L),eq("order-1"));
        verify(db).update(contains("INSERT INTO warranty_records"),any(Object[].class));
        verify(db).update(contains("INSERT INTO warranty_events"),any(Object[].class));
        verify(outbox).append(eq(7L),eq("admin@vendor.test"),eq("WARRANTY"),eq("55"),
            eq("WARRANTY.REGISTERED"),isNull(),anyMap());
    }

    @Test void customerListOnlyQueriesTenantOrderReferencesFromItsOwnPaidHistory() {
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of(
            new OrderHistoryService.CustomerOrderReference("own-order",7L,"Vendor A")));
        when(db.queryForList(contains("WHERE w.tenant_id=? AND w.order_id=?"),
            eq(7L),eq("own-order"))).thenReturn(List.of());
        assertThat(service().customerList("customer@example.test")).isEmpty();
        verify(db).queryForList(contains("WHERE w.tenant_id=? AND w.order_id=?"),eq(7L),eq("own-order"));
    }
}
