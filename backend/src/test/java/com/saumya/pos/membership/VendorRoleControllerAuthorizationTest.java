package com.saumya.pos.membership;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    void roleCreatePermissionCreatesOnlyRole() throws Exception {
        when(service.create(eq("cashier"),eq("Cashier"),eq(List.of("POS_VIEW")),eq("New cashier role")))
            .thenReturn(new VendorRoleService.Role(10,"cashier","Cashier",true,0,List.of("POS_VIEW")));
        mvc.perform(post("/api/v1/vendor/roles").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roleKey\":\"cashier\",\"displayName\":\"Cashier\",\"permissions\":[\"POS_VIEW\"],\"reason\":\"New cashier role\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(10));
        verify(service).create("cashier","Cashier",List.of("POS_VIEW"),"New cashier role");
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
