package com.saumya.pos.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=ServiceRequestController.class)
@Import(com.saumya.pos.AuthorizationTestSecurityConfiguration.class)
class ServiceRequestControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean ServiceRequestService service;

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void vendorListAndDetailRequireServiceView() throws Exception {
        when(service.vendorList()).thenReturn(List.of());
        when(service.vendorGet(12)).thenReturn(null);
        mvc.perform(get("/api/v1/vendor/service-requests")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vendor/service-requests/12")).andExpect(status().isOk());
        verify(service).vendorList();
        verify(service).vendorGet(12);
    }

    @Test @WithMockUser(authorities={"SHIPMENT_VIEW"})
    void unrelatedPermissionCannotReadServiceRequests() throws Exception {
        mvc.perform(get("/api/v1/vendor/service-requests")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vendor/service-requests/12")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"SERVICE_CREATE"})
    void vendorCanReadEligibleOrdersAndCreateServiceRequest() throws Exception {
        when(service.eligibleVendorOrders()).thenReturn(List.of());
        when(service.createForVendor(any())).thenReturn(null);
        mvc.perform(get("/api/v1/vendor/service-requests/orders")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/vendor/service-requests").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"subject\":\"Inverter issue\",\"description\":\"Customer reports intermittent shutdown\",\"priority\":\"HIGH\"}"))
            .andExpect(status().isCreated());
        verify(service).eligibleVendorOrders();
        verify(service).createForVendor(any());
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCannotCreateOrAssignRequests() throws Exception {
        mvc.perform(post("/api/v1/vendor/service-requests").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"subject\":\"Issue\",\"description\":\"Details\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/vendor/service-requests/12/assignment").contentType("application/json")
            .content("{\"expectedVersion\":0,\"assignedTo\":\"engineer@example.test\",\"reason\":\"Assign work\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"SERVICE_ASSIGN"})
    void serviceAssignCanListAssigneesAndReassign() throws Exception {
        when(service.eligibleAssignees()).thenReturn(List.of("engineer@example.test"));
        when(service.assign(eq(12L),any())).thenReturn(null);
        mvc.perform(get("/api/v1/vendor/service-requests/assignees")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/vendor/service-requests/12/assignment").contentType("application/json")
            .content("{\"expectedVersion\":0,\"assignedTo\":\"engineer@example.test\",\"reason\":\"Assign work\"}"))
            .andExpect(status().isOk());
        verify(service).eligibleAssignees();
        verify(service).assign(eq(12L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_UPDATE"})
    void serviceUpdateCanMoveRequestForwardButNotUseResolvePermissionOnItsOwn() throws Exception {
        when(service.updateStatus(eq(12L),any())).thenReturn(null);
        mvc.perform(patch("/api/v1/vendor/service-requests/12/status").contentType("application/json")
            .content("{\"expectedVersion\":0,\"status\":\"IN_PROGRESS\",\"note\":\"Engineer started\"}"))
            .andExpect(status().isOk());
        verify(service).updateStatus(eq(12L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_RESOLVE"})
    void serviceResolveCanCallStatusEndpoint() throws Exception {
        when(service.updateStatus(eq(12L),any())).thenReturn(null);
        mvc.perform(patch("/api/v1/vendor/service-requests/12/status").contentType("application/json")
            .content("{\"expectedVersion\":1,\"status\":\"RESOLVED\",\"note\":\"Issue fixed\"}"))
            .andExpect(status().isOk());
        verify(service).updateStatus(eq(12L),any());
    }

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerCanListCreateAndReadTheirOwnServiceRequestRoutes() throws Exception {
        when(service.eligibleCustomerOrders(anyString())).thenReturn(List.of());
        when(service.customerList(anyString())).thenReturn(List.of());
        when(service.customerGet(anyString(),eq(1L))).thenReturn(null);
        when(service.createForCustomer(anyString(),any())).thenReturn(null);
        mvc.perform(get("/api/v1/customer/service-requests/orders")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/service-requests")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/service-requests/1")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/customer/service-requests").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"subject\":\"Service visit\",\"description\":\"Please arrange a visit\"}"))
            .andExpect(status().isCreated());
        verify(service).eligibleCustomerOrders(anyString());
        verify(service).customerList(anyString());
        verify(service).customerGet(anyString(),eq(1L));
        verify(service).createForCustomer(anyString(),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void vendorCannotCallCustomerServiceRequestRoutes() throws Exception {
        mvc.perform(get("/api/v1/customer/service-requests")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customer/service-requests").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"subject\":\"Issue\",\"description\":\"Details\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerCannotCallVendorServiceRequestRoutes() throws Exception {
        mvc.perform(get("/api/v1/vendor/service-requests")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vendor/service-requests/orders")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
