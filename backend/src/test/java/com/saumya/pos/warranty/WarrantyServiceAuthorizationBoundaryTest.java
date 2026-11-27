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

    @Test void customerListOnlyQueriesTenantOrderReferencesFromItsOwnPaidHistory() {
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of(
            new OrderHistoryService.CustomerOrderReference("own-order",7L,"Vendor A")));
        when(db.queryForList(contains("WHERE w.tenant_id=? AND w.order_id=?"),
            eq(7L),eq("own-order"))).thenReturn(List.of());
        assertThat(service().customerList("customer@example.test")).isEmpty();
        verify(db).queryForList(contains("WHERE w.tenant_id=? AND w.order_id=?"),eq(7L),eq("own-order"));
    }
}
