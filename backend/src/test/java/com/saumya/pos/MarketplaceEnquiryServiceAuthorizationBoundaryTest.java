package com.saumya.pos;

import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.customer.VendorAccessAuditService;
import com.saumya.pos.customer.VendorCustomerService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.marketplace.MarketplaceEnquiryService;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiryRepository;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketplaceEnquiryServiceAuthorizationBoundaryTest {
    private MarketplaceEnquiryService service(AuthorizationContext authorization,
            MarketplaceEnquiryRepository enquiries,VendorAccessAuditService audit) {
        return new MarketplaceEnquiryService(mock(CatalogService.class),enquiries,mock(CustomerAccess.class),
            mock(VendorCustomerService.class),authorization,audit,mock(TenantLifecycleService.class),mock(JdbcTemplate.class),
            Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"),ZoneOffset.UTC));
    }

    @Test void vendorInboxRejectsCustomerBeforeAnyPersistenceAccess() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "customer@example.test",null,null,null,Set.of("CUSTOMER"),false,true,false));
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var audit=mock(VendorAccessAuditService.class);
        var service=service(authorization,enquiries,audit);
        assertThatThrownBy(service::leads).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(enquiries,audit);
    }

    @Test void vendorInboxRequiresDedicatedLeadPermissionBeforeQuerying() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "employee@vendor.test",71L,5L,"71:employee@vendor.test",Set.of("CUSTOMER_RELATION_VIEW"),false,false,true));
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var audit=mock(VendorAccessAuditService.class);
        var service=service(authorization,enquiries,audit);
        assertThatThrownBy(service::leads).isInstanceOfSatisfying(ResponseStatusException.class,
            e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(enquiries,audit);
    }

    @Test void vendorInboxUsesOnlyAuthenticatedTenantAndAuditsTheRead() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "sales@vendor.test",71L,5L,"71:sales@vendor.test",Set.of("LEAD_VIEW","LEAD_ASSIGN"),false,false,true));
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var audit=mock(VendorAccessAuditService.class);
        when(enquiries.findAllByTenantIdOrderByCreatedAtDescIdDesc(71L,PageRequest.of(0,100)))
            .thenReturn(new PageImpl<>(List.of()));
        var service=service(authorization,enquiries,audit);
        assertThat(service.leads()).isEmpty();
        verify(enquiries).findAllByTenantIdOrderByCreatedAtDescIdDesc(71L,PageRequest.of(0,100));
        verify(audit).recordLeadInboxRead();
        verifyNoMoreInteractions(enquiries);
    }

    @Test void publicSubmissionRequiresExplicitSharingConsentBeforeTenantLookup() {
        var catalogue=mock(CatalogService.class);
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var customers=mock(CustomerAccess.class);
        var relationships=mock(VendorCustomerService.class);
        var authorization=mock(AuthorizationContext.class);
        var audit=mock(VendorAccessAuditService.class);
        var tenants=mock(TenantLifecycleService.class);
        var service=new MarketplaceEnquiryService(catalogue,enquiries,customers,relationships,authorization,audit,tenants,mock(JdbcTemplate.class),
            Clock.systemUTC());
        var request=new MarketplaceEnquiryService.Submission("Customer","customer@example.test",null,
            "Please share installation details",null,false,false);
        assertThatThrownBy(()->service.submit("vendor",request,"192.0.2.1",null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(catalogue,enquiries,customers,relationships,tenants);
    }
}
