package com.saumya.pos.membership;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.security.IdentityInvitationPort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendorRolePermissionCatalogueTest {
    @Test
    void catalogueIsLimitedToCurrentActorAndEntitlementFilteredVendorPermissions() {
        var db = mock(JdbcTemplate.class);
        var auth = mock(AuthorizationContext.class);
        var invitations = mock(IdentityInvitationPort.class);
        var memberships = mock(MembershipService.class);
        var actor = new AuthorizationContext.Context("admin@vendor.test", 7L, 12L,
            "7:admin@vendor.test",
            Set.of("ROLE_VIEW", "ROLE_CREATE", "PRODUCT_VIEW", "POS_VIEW"), false, false, true);
        when(auth.current()).thenReturn(actor);
        // POS_VIEW is absent because MembershipService has already applied the
        // active tenant's subscription entitlement to its effective grants.
        when(memberships.access("admin@vendor.test")).thenReturn(Optional.of(
            new MembershipService.Access(7L, 12L, "admin@vendor.test",
                List.of("ROLE_VIEW", "ROLE_CREATE", "PLATFORM_TENANT_CREATE", "UNKNOWN_PERMISSION"))));
        var service = new VendorRoleService(db, auth, invitations, memberships);

        assertThat(service.delegablePermissions()).containsExactly("ROLE_CREATE", "ROLE_VIEW");

        verify(memberships).access("admin@vendor.test");
        verifyNoInteractions(db, invitations);
    }

    @Test
    void actorWithoutRoleAdministrationCannotReadDelegablePermissionCatalogue() {
        var db = mock(JdbcTemplate.class);
        var auth = mock(AuthorizationContext.class);
        var memberships = mock(MembershipService.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "cashier@vendor.test", 7L, 12L, "7:cashier@vendor.test",
            Set.of("POS_VIEW"), false, false, true));
        var service = new VendorRoleService(db, auth, mock(IdentityInvitationPort.class), memberships);

        assertThatThrownBy(service::delegablePermissions)
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(memberships, db);
    }
}
