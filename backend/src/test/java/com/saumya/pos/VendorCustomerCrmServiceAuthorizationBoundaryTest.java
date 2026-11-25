package com.saumya.pos;

import com.saumya.pos.customer.VendorCustomerCrmService;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorCustomerCrmServiceAuthorizationBoundaryTest {
    @Test void customerActorIsRejectedBeforeDatabaseAccess() {
        var jdbc=mock(JdbcTemplate.class); var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("customer@example.test",null,null,null,
            Set.of("CUSTOMER"),false,true,false));
        var service=new VendorCustomerCrmService(jdbc,auth);
        assertThatThrownBy(service::customers).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(jdbc);
    }

    @Test void eachOperationRequiresItsOwnVendorPermissionBeforeDatabaseAccess() {
        var jdbc=mock(JdbcTemplate.class); var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("employee@vendor.test",7L,11L,
            "7:employee@vendor.test",Set.of("PRODUCT_VIEW"),false,false,true));
        var service=new VendorCustomerCrmService(jdbc,auth);
        assertThatThrownBy(service::customers).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->service.notes(99L)).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->service.createNote(99L,"Private note","Test")).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->service.updateNote(99L,1L,0L,"Changed note","Test")).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(jdbc);
    }

    @Test void customerRelationshipMustBelongToActiveVendorTenant() {
        var jdbc=mock(JdbcTemplate.class); var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("employee@vendor-a.test",7L,11L,
            "7:employee@vendor-a.test",Set.of("CRM_NOTE_VIEW"),false,false,true));
        when(jdbc.queryForObject(contains("FROM tenant_customers"),eq(Boolean.class),eq(7L),eq(99L))).thenReturn(false);
        var service=new VendorCustomerCrmService(jdbc,auth);
        assertThatThrownBy(()->service.notes(99L)).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(jdbc).queryForObject(contains("WHERE tenant_id=? AND customer_id=?"),eq(Boolean.class),eq(7L),eq(99L));
        verifyNoMoreInteractions(jdbc);
    }
}
