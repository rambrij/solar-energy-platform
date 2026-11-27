package com.saumya.pos.installation;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=InstallationController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class InstallationControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean InstallationService service;

    @Test @WithMockUser(authorities={"INSTALLATION_VIEW"})
    void vendorListRequiresInstallationView() throws Exception {
        when(service.vendorList()).thenReturn(List.of());
        mvc.perform(get("/api/v1/vendor/installations")).andExpect(status().isOk());
        verify(service).vendorList();
    }

    @Test @WithMockUser(authorities={"INSTALLATION_VIEW"})
    void vendorGetRequiresInstallationView() throws Exception {
        when(service.vendorGet(10)).thenReturn(null);
        mvc.perform(get("/api/v1/vendor/installations/10")).andExpect(status().isOk());
        verify(service).vendorGet(10);
    }

    @Test @WithMockUser(authorities={"SHIPMENT_VIEW"})
    void shipmentViewDoesNotGrantInstallationView() throws Exception {
        mvc.perform(get("/api/v1/vendor/installations")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"INSTALLATION_ASSIGN"})
    void eligibleOrdersAndEngineersRequireInstallationAssign() throws Exception {
        when(service.eligibleOrders()).thenReturn(List.of());
        when(service.eligibleEngineers()).thenReturn(List.of());
        mvc.perform(get("/api/v1/vendor/installations/orders")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vendor/installations/engineers")).andExpect(status().isOk());
        verify(service).eligibleOrders();
        verify(service).eligibleEngineers();
    }

    @Test @WithMockUser(authorities={"INSTALLATION_VIEW"})
    void installationViewCannotCreateOrReassign() throws Exception {
        mvc.perform(post("/api/v1/vendor/installations").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"siteAddress\":\"Solar site\",\"scheduledAt\":\"2026-12-10T10:00:00Z\",\"assignedTo\":\"engineer@example.test\",\"reason\":\"Confirmed\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/vendor/installations/1/assignment").contentType("application/json")
            .content("{\"expectedVersion\":0,\"assignedTo\":\"engineer@example.test\",\"scheduledAt\":\"2026-12-10T10:00:00Z\",\"reason\":\"Reassigned\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"INSTALLATION_ASSIGN"})
    void managerCanCreateAndAssignInstallation() throws Exception {
        var job=new InstallationService.Job(1,"order-1","Solar site",Instant.parse("2026-12-10T10:00:00Z"),
            "ASSIGNED","engineer@example.test",Instant.now(),Instant.now(),null,0);
        when(service.create(any())).thenReturn(job);
        when(service.assign(eq(1L),any())).thenReturn(job);
        mvc.perform(post("/api/v1/vendor/installations").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"siteAddress\":\"Solar site\",\"scheduledAt\":\"2026-12-10T10:00:00Z\",\"assignedTo\":\"engineer@example.test\",\"reason\":\"Confirmed\"}"))
            .andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/vendor/installations/1/assignment").contentType("application/json")
            .content("{\"expectedVersion\":0,\"assignedTo\":\"engineer@example.test\",\"scheduledAt\":\"2026-12-10T10:00:00Z\",\"reason\":\"Reassigned\"}"))
            .andExpect(status().isOk());
        verify(service).create(any());
        verify(service).assign(eq(1L),any());
    }

    @Test @WithMockUser(authorities={"INSTALLATION_UPDATE"})
    void assignedEngineerCanSubmitProgress() throws Exception {
        var job=new InstallationService.Job(1,"order-1","Solar site",Instant.parse("2026-12-10T10:00:00Z"),
            "IN_PROGRESS","engineer@example.test",Instant.now(),Instant.now(),null,1);
        when(service.updateStatus(eq(1L),any())).thenReturn(job);
        mvc.perform(patch("/api/v1/vendor/installations/1/status").contentType("application/json")
            .content("{\"expectedVersion\":0,\"status\":\"IN_PROGRESS\",\"note\":\"Started\"}"))
            .andExpect(status().isOk());
        verify(service).updateStatus(eq(1L),any());
    }

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerCanReadOwnInstallationRoutes() throws Exception {
        when(service.customerList(anyString())).thenReturn(List.of());
        when(service.customerGet(anyString(),eq(1L))).thenReturn(null);
        mvc.perform(get("/api/v1/customer/installations")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/installations/1")).andExpect(status().isOk());
        verify(service).customerList(anyString());
        verify(service).customerGet(anyString(),eq(1L));
    }

    @Test @WithMockUser(authorities={"INSTALLATION_VIEW"})
    void vendorCannotCallCustomerInstallationRoutes() throws Exception {
        mvc.perform(get("/api/v1/customer/installations")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customer/installations/1")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
