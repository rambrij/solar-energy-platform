package com.saumya.pos.membership;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VendorRoleController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class VendorRoleControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean VendorRoleService service;

    @Test void anonymousCannotListRoles() throws Exception {
        mvc.perform(get("/api/v1/vendor/roles")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerCannotAccessVendorRoles() throws Exception {
        mvc.perform(get("/api/v1/vendor/roles")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vendor/roles/permissions")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"ROLE_VIEW"})
    void roleViewCanReadButCannotCreateOrChangeRoles() throws Exception {
        when(service.roles()).thenReturn(List.of());
        mvc.perform(get("/api/v1/vendor/roles")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/vendor/roles").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roleKey\":\"cashier\",\"displayName\":\"Cashier\",\"permissions\":[\"POS_VIEW\"],\"reason\":\"Create\u0020role\"}"))
            .andExpect(status().isForbidden());
        verify(service).roles();verifyNoMoreInteractions(service);
    }

    @Test @WithMockUser(authorities={"ROLE_CREATE"})
    void roleCreatorCanReadOnlyTheServerResolvedDelegablePermissionList() throws Exception {
        when(service.delegablePermissions()).thenReturn(List.of("ROLE_CREATE", "PRODUCT_VIEW"));
        mvc.perform(get("/api/v1/vendor/roles/permissions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[0]").value("ROLE_CREATE"))
            .andExpect(jsonPath("$.permissions[1]").value("PRODUCT_VIEW"));
        verify(service).delegablePermissions();
    }

    @Test @WithMockUser(authorities={"ROLE_CREATE"})
    void roleCreatePermissionCreatesOnlyRole() throws Exception {
        when(service.create(eq("cashier"),eq("Cashier"),eq(List.of("POS_VIEW")),eq("New cashier role")))
            .thenReturn(new VendorRoleService.Role(10,"cashier","Cashier",true,0,List.of("POS_VIEW")));
        mvc.perform(post("/api/v1/vendor/roles").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roleKey\":\"cashier\",\"displayName\":\"Cashier\",\"permissions\":[\"POS_VIEW\"],\"reason\":\"New cashier role\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(10));
        verify(service).create("cashier","Cashier",List.of("POS_VIEW"),"New cashier role");
    }

    @Test @WithMockUser(authorities={"ROLE_UPDATE"})
    void staleRoleVersionUsesTheExistingRfc9457ConflictResponse() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Role version is stale"))
            .when(service).update(eq(42L), eq(2L), eq("Updated role"),
                eq(List.of("POS_VIEW")), eq("Concurrency regression"));
        mvc.perform(put("/api/v1/vendor/roles/42")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":2,\"displayName\":\"Updated role\",\"permissions\":[\"POS_VIEW\"],\"reason\":\"Concurrency regression\"}"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("Role version is stale"))
            .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
        verify(service).update(42L, 2L, "Updated role", List.of("POS_VIEW"), "Concurrency regression");
    }

    @Test @WithMockUser(authorities={"USER_CREATE"})
    void employeeInvitationRequiresBothUserCreateAndRoleAssign() throws Exception {
        mvc.perform(post("/api/v1/vendor/employees").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"staff@vendor.test\",\"branchId\":1,\"roleId\":2,\"reason\":\"Invite employee\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"USER_CREATE","ROLE_ASSIGN"})
    void employeeInvitationUsesExactAdminPolicy() throws Exception {
        when(service.createEmployee("staff@vendor.test",1,2,"Invite employee"))
            .thenReturn(new VendorRoleService.EmployeeInvitation("staff@vendor.test","one-time-token","2026-11-16T12:30:00Z",false));
        mvc.perform(post("/api/v1/vendor/employees").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"staff@vendor.test\",\"branchId\":1,\"roleId\":2,\"reason\":\"Invite employee\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.invitationToken").value("one-time-token"));
        verify(service).createEmployee("staff@vendor.test",1,2,"Invite employee");
    }

    @Test @WithMockUser(authorities={"PLATFORM_IDENTITY_MANAGE"})
    void platformAdminCannotOperateVendorRoleApi() throws Exception {
        mvc.perform(get("/api/v1/vendor/roles")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
