package com.saumya.pos.membership;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.security.IdentityInvitationPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorRoleServiceReconciliationTest {
    @Test void reconciliationReturnsTenantScopedCountsWithoutExposingGrantValues(){
        var db=mock(JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test",7L,12L,
            java.util.Set.of("USER_VIEW"),false,false,true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForObject(anyString(),eq(Long.class),eq(7L))).thenReturn(2L);
        when(db.queryForList(contains("SELECT DISTINCT p.username,p.permission"),eq(7L)))
            .thenReturn(List.of(
                Map.of("username","employee@vendor.test","permission","NOT_A_PERMISSION"),
                Map.of("username","employee@vendor.test","permission","PLATFORM_TENANT_CREATE")));
        var service=new VendorRoleService(db,auth,mock(IdentityInvitationPort.class),mock(com.saumya.pos.membership.MembershipService.class));

        Map<String,Long> result=service.permissionReconciliation();

        assertThat(result).containsEntry("active_employees_with_active_roles",2L)
            .containsEntry("legacy_grants_without_any_role_assignment",2L)
            .containsEntry("active_employees_without_roles_or_legacy_grants",2L)
            .containsEntry("employees_with_only_inactive_or_deactivated_roles",2L)
            .containsEntry("invalid_or_unknown_legacy_permission_grants",1L)
            .containsEntry("platform_permissions_in_vendor_legacy_grants",1L)
            .containsEntry("orphaned_or_cross_tenant_role_assignments",2L);
        verify(db,times(5)).queryForObject(anyString(),eq(Long.class),eq(7L));
    }

    @Test void reconciliationRequiresVendorUserViewBeforeDatabaseAccess(){
        var db=mock(JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN,"No vendor actor"));
        var service=new VendorRoleService(db,auth,mock(IdentityInvitationPort.class),mock(com.saumya.pos.membership.MembershipService.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(service::permissionReconciliation)
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(db);
    }
}
