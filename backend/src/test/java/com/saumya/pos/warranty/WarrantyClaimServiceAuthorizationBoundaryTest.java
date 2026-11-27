package com.saumya.pos.warranty;

import com.saumya.pos.catalog.ObjectStoragePort;
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

class WarrantyClaimServiceAuthorizationBoundaryTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final AuthorizationContext authorization=mock(AuthorizationContext.class);
    private final OrderHistoryService orders=mock(OrderHistoryService.class);
    private final EventOutbox outbox=mock(EventOutbox.class);
    private final ObjectStoragePort storage=mock(ObjectStoragePort.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"),ZoneOffset.UTC);
    private WarrantyClaimService service(){return new WarrantyClaimService(db,authorization,orders,outbox,storage,clock);}
    private void customer(String username){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            username,null,null,null,Set.of("CUSTOMER"),false,true,false));
    }
    private void vendor(Set<String> grants){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "engineer@vendor.test",7L,2L,"7:engineer",grants,false,false,true));
    }

    @Test void customerCannotSubmitClaimForForeignWarranty(){
        customer("customer@example.test");
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of());
        assertThatThrownBy(()->service().submit("customer@example.test",
            new WarrantyClaimService.Create(99,"Panel issue","Panel stopped working")))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->
                assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
        verifyNoInteractions(db,outbox,storage);
    }

    @Test void customerCannotReadClaimOutsideTheirPaidOrderHistory(){
        customer("customer@example.test");
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of());
        assertThatThrownBy(()->service().customerGet("customer@example.test",99))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->
                assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
        verifyNoInteractions(db,outbox,storage);
    }

    @Test void claimSubmissionIsRejectedOutsideInclusiveCoverageWindow(){
        customer("customer@example.test");
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of(
            new OrderHistoryService.CustomerOrderReference("own-order",7L,"Vendor A")));
        var warranty=new java.util.HashMap<String,Object>();
        warranty.put("id",99L);warranty.put("order_id","own-order");
        warranty.put("coverage_start_date",java.sql.Date.valueOf("2026-11-28"));
        warranty.put("coverage_end_date",java.sql.Date.valueOf("2030-11-27"));
        warranty.put("product_name","Solar Panel");
        when(db.queryForList(contains("SELECT w.id,w.order_id"),eq(7L),eq("own-order"),eq(99L)))
            .thenReturn(List.of(warranty));
        assertThatThrownBy(()->service().submit("customer@example.test",
            new WarrantyClaimService.Create(99,"Panel issue","Panel stopped working")))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->
                assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT));
        verify(db,never()).update(anyString(),any(Object[].class));
        verifyNoInteractions(outbox,storage);
    }

    @Test void vendorMustHaveServiceViewBeforeClaimQueries(){
        vendor(Set.of("SERVICE_UPDATE"));
        assertThatThrownBy(service()::vendorList).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,orders,outbox,storage);
    }

    @Test void serviceEngineerCannotAssignWithoutServiceAssign(){
        vendor(Set.of("SERVICE_VIEW","SERVICE_UPDATE"));
        assertThatThrownBy(()->service().assign(9,new WarrantyClaimService.Assignment(0,"other@vendor.test","Assign")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,outbox,storage);
    }
}
