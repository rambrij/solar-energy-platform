package com.saumya.pos;

import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.membership.VendorRoleService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.security.IdentityInvitationPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class VendorRoleServiceAuthorizationBoundaryTest {
    @Test
    void everyRoleAndEmployeeEntryPointRejectsMissingIdentityBeforeJdbcOrInvitationAccess() {
        var db = mock(JdbcTemplate.class);
        var auth = mock(AuthorizationContext.class);
        var invitations = mock(IdentityInvitationPort.class);
        var memberships = mock(MembershipService.class);
        when(auth.current()).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No vendor actor"));
        var service = new VendorRoleService(db, auth, invitations, memberships);

        denied(() -> service.roles());
        denied(() -> service.role(1L));
        denied(() -> service.create("new-role", "New role", List.of("POS_VIEW"), "test"));
        denied(() -> service.update(1L, 0L, "Changed role", List.of("POS_VIEW"), "test"));
        denied(() -> service.deactivate(1L, "test"));
        denied(() -> service.employees());
        denied(() -> service.createEmployee("new.employee@vendor.test", 2L, 1L, "test"));
        denied(() -> service.assignRoles("employee@vendor.test", List.of(1L), "test"));
        denied(() -> service.assignRegisters("employee@vendor.test", List.of(1L), "test"));
        denied(() -> service.updateBranch("employee@vendor.test", 2L, "test"));
        denied(() -> service.disableEmployee("employee@vendor.test", "test"));
        denied(() -> service.effectivePermissions("employee@vendor.test"));

        verifyNoInteractions(db, invitations, memberships);
        verify(auth, times(12)).current();
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }
}
