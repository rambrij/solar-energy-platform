package com.saumya.pos;

import com.saumya.pos.customer.VendorCustomerCrmService;
import com.saumya.pos.customer.internal.VendorAccessAuditRepository;
import com.saumya.pos.customer.internal.VendorCustomerCrmNoteRepository;
import com.saumya.pos.customer.internal.VendorCustomerRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorCustomerCrmServiceAuthorizationBoundaryTest {
    @Test void customerActorIsRejectedBeforePersistenceAccess() {
        var relationships=mock(VendorCustomerRepository.class);
        var notes=mock(VendorCustomerCrmNoteRepository.class);
        var audit=mock(VendorAccessAuditRepository.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("customer@example.test",null,null,null,
            Set.of("CUSTOMER"),false,true,false));
        var service=new VendorCustomerCrmService(relationships,notes,audit,auth);
        assertThatThrownBy(service::customers).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(relationships,notes,audit);
    }

    @Test void eachOperationRequiresItsOwnVendorPermissionBeforePersistenceAccess() {
        var relationships=mock(VendorCustomerRepository.class);
        var notes=mock(VendorCustomerCrmNoteRepository.class);
        var audit=mock(VendorAccessAuditRepository.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("employee@vendor.test",7L,11L,
            "7:employee@vendor.test",Set.of("PRODUCT_VIEW"),false,false,true));
        var service=new VendorCustomerCrmService(relationships,notes,audit,auth);
        assertThatThrownBy(service::customers).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->service.notes(99L)).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->service.createNote(99L,"Private note","Test")).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->service.updateNote(99L,1L,0L,"Changed note","Test")).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(relationships,notes,audit);
    }

    @Test void customerRelationshipMustBelongToActiveVendorTenant() {
        var relationships=mock(VendorCustomerRepository.class);
        var notes=mock(VendorCustomerCrmNoteRepository.class);
        var audit=mock(VendorAccessAuditRepository.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("employee@vendor-a.test",7L,11L,
            "7:employee@vendor-a.test",Set.of("CRM_NOTE_VIEW"),false,false,true));
        when(relationships.existsByTenantAndCustomer(7L,99L)).thenReturn(false);
        var service=new VendorCustomerCrmService(relationships,notes,audit,auth);
        assertThatThrownBy(()->service.notes(99L)).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(relationships).existsByTenantAndCustomer(7L,99L);
        verifyNoInteractions(notes,audit);
    }
}
