package com.saumya.pos.membership;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.security.IdentityInvitationPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorRoleServiceProvisioningTest {
    @Test void vendorCreatedEmployeeGetsExplicitRoleAssignmentBeforeInvitationCompletes(){
        var db=mock(JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        var invitations=mock(IdentityInvitationPort.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test", 7L, 12L, String.valueOf(7L) + ":" + ("admin@vendor.test"), java.util.Set.of("USER_CREATE","ROLE_ASSIGN","POS_VIEW"), false, false, true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForList(startsWith("SELECT id,role_key,display_name,active,version"),eq(7L),eq(41L)))
            .thenReturn(List.of(Map.of("id",41L,"role_key","cashier","display_name","Cashier","active",true,"version",0L)));
        when(db.queryForList(startsWith("SELECT permission FROM vendor_role_permissions"),eq(String.class),eq(7L),eq(41L)))
            .thenReturn(List.of("POS_VIEW"));
        when(db.queryForObject(startsWith("SELECT COUNT(*)>0 FROM branches"),eq(Boolean.class),eq(7L),eq(12L)))
            .thenReturn(true);
        when(db.queryForObject(startsWith("SELECT COUNT(*)>0 FROM employees"),eq(Boolean.class),eq(7L),eq("cashier@vendor.test")))
            .thenReturn(false);
        when(invitations.issueInvitation("cashier@vendor.test"))
            .thenReturn(new IdentityInvitationPort.Invitation("cashier@vendor.test","opaque-token",Instant.parse("2026-11-19T00:00:00Z"),false));
        var service=new VendorRoleService(db,auth,invitations,mock(MembershipService.class));

        var result=service.createEmployee("Cashier@Vendor.Test",12L,41L,"approved by vendor admin");

        assertThat(result.username()).isEqualTo("cashier@vendor.test");
        assertThat(result.invitationToken()).isEqualTo("opaque-token");
        verify(db).update(contains("INSERT INTO employees"),eq("cashier@vendor.test"),eq(7L),eq(12L));
        verify(db).update(contains("INSERT INTO employee_role_assignments"),eq(7L),eq(12L),eq("cashier@vendor.test"),eq(41L),eq("admin@vendor.test"));
        verify(invitations).issueInvitation("cashier@vendor.test");
    }

    @Test void employeeAccessHistoryReadsOnlyCurrentTenantSubjectAndReturnsNewestEvents(){
        var db=mock(JdbcTemplate.class);var auth=mock(AuthorizationContext.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test",7L,12L,"7:admin@vendor.test",java.util.Set.of("USER_VIEW"),false,false,true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForList(startsWith("SELECT username,branch_id,enabled"),eq(7L),eq("staff@vendor.test")))
            .thenReturn(List.of(Map.of("username","staff@vendor.test","branch_id",12L,"enabled",true)));
        var occurred=java.sql.Timestamp.from(Instant.parse("2026-11-26T09:00:00Z"));
        when(db.queryForList(contains("FROM vendor_access_audit_events"),eq(7L),eq("staff@vendor.test")))
            .thenReturn(List.of(Map.of("id",91L,"actor_username","admin@vendor.test","resource_type","ROLE_ASSIGNMENT",
                "resource_id","41","action","ROLE_ASSIGNED","reason","Approved onboarding","occurred_at",occurred)));
        var service=new VendorRoleService(db,auth,mock(IdentityInvitationPort.class),mock(MembershipService.class));
        var result=service.employeeAccessHistory("STAFF@VENDOR.TEST");
        assertThat(result).containsExactly(new VendorRoleService.EmployeeAccessEvent(91,"admin@vendor.test","ROLE_ASSIGNMENT","41",
            "ROLE_ASSIGNED","Approved onboarding",occurred.toInstant()));
        org.mockito.ArgumentCaptor<String> sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(db,times(2)).queryForList(sql.capture(),eq(7L),eq("staff@vendor.test"));
        String auditQuery=sql.getAllValues().stream()
            .filter(statement->statement.contains("FROM vendor_access_audit_events"))
            .findFirst().orElseThrow();
        assertThat(auditQuery).contains("tenant_id=?","subject_username=?","ORDER BY occurred_at DESC,id DESC","LIMIT 50");
    }

    @Test void employeeAccessHistoryDoesNotQueryAuditForAnEmployeeOutsideTheVendorTenant(){
        var db=mock(JdbcTemplate.class);var auth=mock(AuthorizationContext.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test",7L,12L,"7:admin@vendor.test",java.util.Set.of("USER_VIEW"),false,false,true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForList(startsWith("SELECT username,branch_id,enabled"),eq(7L),eq("other@vendor.test")))
            .thenReturn(List.of());
        var service=new VendorRoleService(db,auth,mock(IdentityInvitationPort.class),mock(MembershipService.class));
        assertThatThrownBy(()->service.employeeAccessHistory("other@vendor.test"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.NOT_FOUND);
        verify(db,never()).queryForList(contains("FROM vendor_access_audit_events"),any(Object[].class));
    }

    @Test void invitationStatusIsReadOnlyAndRequiresEmployeeInTheActiveTenant(){
        var db=mock(JdbcTemplate.class);var auth=mock(AuthorizationContext.class);var invitations=mock(IdentityInvitationPort.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test",7L,12L,"7:admin@vendor.test",java.util.Set.of("USER_VIEW"),false,false,true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForList(startsWith("SELECT username,branch_id,enabled"),eq(7L),eq("staff@vendor.test")))
            .thenReturn(List.of(Map.of("username","staff@vendor.test","branch_id",12L,"enabled",true)));
        when(invitations.invitationStatus("staff@vendor.test"))
            .thenReturn(new IdentityInvitationPort.InvitationStatus("PENDING",Instant.parse("2026-11-28T00:00:00Z")));
        var service=new VendorRoleService(db,auth,invitations,mock(MembershipService.class));
        assertThat(service.invitationStatus("STAFF@VENDOR.TEST").status()).isEqualTo("PENDING");
        verify(invitations).invitationStatus("staff@vendor.test");
        verify(db,never()).update(anyString(),any(Object[].class));
    }

    @Test void resendRotatesInvitationOnlyForTenantOwnedEmployeeAndAuditsReason(){
        var db=mock(JdbcTemplate.class);var auth=mock(AuthorizationContext.class);var invitations=mock(IdentityInvitationPort.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test",7L,12L,"7:admin@vendor.test",java.util.Set.of("USER_CREATE","ROLE_ASSIGN"),false,false,true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForList(startsWith("SELECT username,branch_id,enabled"),eq(7L),eq("staff@vendor.test")))
            .thenReturn(List.of(Map.of("username","staff@vendor.test","branch_id",12L,"enabled",true)));
        when(invitations.issueInvitation("staff@vendor.test")).thenReturn(
            new IdentityInvitationPort.Invitation("staff@vendor.test","rotated-token",Instant.parse("2026-11-28T00:00:00Z"),false));
        var service=new VendorRoleService(db,auth,invitations,mock(MembershipService.class));
        var result=service.resendEmployeeInvitation("STAFF@VENDOR.TEST","Follow up with employee");
        assertThat(result.invitationToken()).isEqualTo("rotated-token");
        assertThat(result.expiresAt()).isEqualTo("2026-11-28T00:00:00Z");
        verify(db).update(contains("INSERT INTO vendor_access_audit_events"),eq(7L),eq("admin@vendor.test"),eq("staff@vendor.test"),
            eq("EMPLOYEE_INVITATION"),eq("staff@vendor.test"),eq("EMPLOYEE_INVITATION_RESENT"),eq("Follow up with employee"));
    }

    @Test void vendorCreatedEmployeeCannotBeProvisionedWithoutAValidRole(){
        var db=mock(JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test", 7L, 12L, String.valueOf(7L) + ":" + ("admin@vendor.test"), java.util.Set.of("USER_CREATE","ROLE_ASSIGN","POS_VIEW"), false, false, true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForList(startsWith("SELECT id,role_key,display_name,active,version"),eq(7L),eq(41L)))
            .thenReturn(List.of(Map.of("id",41L,"role_key","disabled","display_name","Disabled","active",false,"version",0L)));
        var invitations=mock(IdentityInvitationPort.class);
        var service=new VendorRoleService(db,auth,invitations,mock(MembershipService.class));
        assertThatThrownBy(()->service.createEmployee("cashier@vendor.test",12L,41L,"reason"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(invitations);
        verify(db,never()).update(contains("INSERT INTO employees"),any(Object[].class));
    }

    @Test void crossTenantRoleIdCannotBeAssignedToAnEmployee(){
        var db=mock(JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        var actor=new AuthorizationContext.Context("admin@vendor.test", 7L, 12L, String.valueOf(7L) + ":" + ("admin@vendor.test"), java.util.Set.of("ROLE_ASSIGN"), false, false, true);
        when(auth.current()).thenReturn(actor);
        when(db.queryForList(startsWith("SELECT username,branch_id,enabled"),eq(7L),eq("employee@vendor.test")))
            .thenReturn(List.of(Map.of("username","employee@vendor.test","branch_id",12L,"enabled",true)));
        // The role exists in another tenant, but the tenant-scoped lookup must not find it.
        when(db.queryForList(startsWith("SELECT id,role_key,display_name,active,version"),eq(7L),eq(99L)))
            .thenReturn(List.of());
        var service=new VendorRoleService(db,auth,mock(IdentityInvitationPort.class),mock(MembershipService.class));

        assertThatThrownBy(()->service.assignRoles("employee@vendor.test",List.of(99L),"cross tenant probe"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.NOT_FOUND);
        verify(db,never()).update(contains("UPDATE employee_role_assignments"),any(Object[].class));
        verify(db,never()).update(contains("INSERT INTO employee_role_assignments"),any(Object[].class));
    }

}
