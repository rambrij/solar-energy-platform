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
import static org.mockito.Mockito.*;

class ServiceVisitServiceAuthorizationBoundaryTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final AuthorizationContext authorization=mock(AuthorizationContext.class);
    private final OrderHistoryService orders=mock(OrderHistoryService.class);
    private final EventOutbox outbox=mock(EventOutbox.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-28T12:00:00Z"),ZoneOffset.UTC);
    private ServiceVisitService service(){return new ServiceVisitService(db,authorization,orders,outbox,clock);}
    private void vendor(Set<String> grants){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "engineer@vendor.test",7L,2L,"7:engineer",grants,false,false,true));
    }

    @Test void viewOnlyEmployeeCannotScheduleOrReassignVisits(){
        vendor(Set.of("SERVICE_VIEW"));
        assertThatThrownBy(()->service().create(new ServiceVisitService.Create(
            "SERVICE_REQUEST",9,"Solar site",clock.instant().plusSeconds(3600),"engineer@vendor.test","Reason")))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service().assign(9,new ServiceVisitService.Assignment(
            0,"engineer@vendor.test",clock.instant().plusSeconds(3600),"Reason")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,orders,outbox);
    }

    @Test void unrelatedVendorGrantCannotReadVisitQueue(){
        vendor(Set.of("SHIPMENT_VIEW","SHIPMENT_UPDATE"));
        assertThatThrownBy(service()::vendorList).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,orders,outbox);
    }

    @Test void customerVisitLookupUsesOnlyAuthenticatedPaidOrderReferences(){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "customer@example.test",null,null,null,Set.of("CUSTOMER"),false,true,false));
        when(orders.customerPaidOnlineOrderReferences("customer@example.test")).thenReturn(List.of());
        assertThatThrownBy(()->service().customerGet("customer@example.test",77))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->
                assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
        verify(orders).customerPaidOnlineOrderReferences("customer@example.test");
        verifyNoInteractions(db,outbox);
    }

    @Test void mismatchedCustomerPrincipalIsRejectedBeforeOrderLookup(){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "someone-else@example.test",null,null,null,Set.of("CUSTOMER"),false,true,false));
        assertThatThrownBy(()->service().customerList("customer@example.test"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,orders,outbox);
    }
}
