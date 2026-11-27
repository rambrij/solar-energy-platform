package com.saumya.pos.installation;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstallationServiceAuthorizationBoundaryTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final AuthorizationContext authorization=mock(AuthorizationContext.class);
    private final OrderHistoryService orders=mock(OrderHistoryService.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"),ZoneOffset.UTC);

    private InstallationService service(){return new InstallationService(db,authorization,orders,clock);}
    private void vendorContext(Set<String> permissions) {
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "engineer@vendor.test",7L,2L,"7:engineer",permissions,false,false,true));
    }

    @Test
    void unrelatedShipmentGrantCannotReadOrMutateInstallationData() {
        vendorContext(Set.of("SHIPMENT_VIEW","SHIPMENT_UPDATE"));
        var service=service();
        assertThatThrownBy(service::eligibleOrders).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(service::eligibleEngineers).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(service::vendorList).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.vendorGet(10)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.create(new InstallationService.Create("order-1","Site",clock.instant().plusSeconds(600),"engineer@vendor.test","Reason")))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.assign(10,new InstallationService.Assign(0,"engineer@vendor.test",clock.instant().plusSeconds(600),"Reason")))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.updateStatus(10,new InstallationService.StatusChange(0,"IN_PROGRESS","Started")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,orders);
    }

    @Test
    void customerListUsesOnlyPaidOrderReferencesReturnedForTheCustomer() {
        var reference=new OrderHistoryService.CustomerOrderReference("owned-order",7L,"Vendor");
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of(reference));
        when(db.queryForList(contains("FROM installation_jobs WHERE tenant_id=? AND order_id=?"),
            eq(7L),eq("owned-order"))).thenReturn(List.of(Map.of(
                "id",11L,"order_id","owned-order","site_address","Site",
                "scheduled_at",java.sql.Timestamp.from(clock.instant().plusSeconds(600)),
                "status","IN_PROGRESS","created_at",java.sql.Timestamp.from(clock.instant()),
                "updated_at",java.sql.Timestamp.from(clock.instant())
            )));
        var rows=service().customerList("customer@example.test");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().orderId()).isEqualTo("owned-order");
        verify(orders).customerPaidOnlineOrderReferences("customer@example.test");
        verify(db).queryForList(contains("WHERE tenant_id=? AND order_id=?"),eq(7L),eq("owned-order"));
    }
}
