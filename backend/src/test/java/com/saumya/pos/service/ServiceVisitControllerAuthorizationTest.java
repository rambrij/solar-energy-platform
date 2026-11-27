package com.saumya.pos.service;

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

@WebMvcTest(controllers=ServiceVisitController.class)
@Import(com.saumya.pos.AuthorizationTestSecurityConfiguration.class)
class ServiceVisitControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean ServiceVisitService visits;

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCanReadVendorVisitQueue(){
        when(visits.vendorList()).thenReturn(List.of());
        try{mvc.perform(get("/api/v1/vendor/service-visits")).andExpect(status().isOk());}
        catch(Exception ex){throw new AssertionError(ex);}
        verify(visits).vendorList();
    }

    @Test @WithMockUser(authorities={"SERVICE_ASSIGN"})
    void serviceAssignCanScheduleForEitherCaseTypeAndListAssignees() throws Exception {
        when(visits.eligibleAssignees()).thenReturn(List.of("engineer@vendor.test"));
        when(visits.create(any())).thenReturn(null);
        mvc.perform(get("/api/v1/vendor/service-visits/assignees")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/vendor/service-visits").contentType("application/json")
            .content("{\"caseType\":\"WARRANTY_CLAIM\",\"caseId\":9,\"siteAddress\":\"Solar site\",\"scheduledAt\":\"2026-12-01T10:00:00Z\",\"assignedTo\":\"engineer@vendor.test\",\"reason\":\"Customer visit\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/vendor/service-visits").contentType("application/json")
            .content("{\"caseType\":\"SERVICE_REQUEST\",\"caseId\":10,\"siteAddress\":\"Solar site\",\"scheduledAt\":\"2026-12-02T10:00:00Z\",\"assignedTo\":\"engineer@vendor.test\",\"reason\":\"Customer visit\"}"))
            .andExpect(status().isCreated());
        verify(visits,times(2)).create(any());
    }

    @Test @WithMockUser(authorities={"SERVICE_ASSIGN"})
    void assignmentIsProtectedByServiceAssign() throws Exception {
        when(visits.assign(eq(11L),any())).thenReturn(null);
        mvc.perform(patch("/api/v1/vendor/service-visits/11/assignment").contentType("application/json")
            .content("{\"expectedVersion\":0,\"assignedTo\":\"engineer@vendor.test\",\"scheduledAt\":\"2026-12-01T10:00:00Z\",\"reason\":\"Shift change\"}"))
            .andExpect(status().isOk());
        verify(visits).assign(eq(11L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_UPDATE"})
    void engineerCanRecordProgressAndCustomerVisibleNote() throws Exception {
        when(visits.updateStatus(eq(11L),any())).thenReturn(null);
        when(visits.recordNote(eq(11L),any())).thenReturn(null);
        mvc.perform(patch("/api/v1/vendor/service-visits/11/status").contentType("application/json")
            .content("{\"expectedVersion\":0,\"status\":\"IN_PROGRESS\",\"note\":\"Visit started\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/vendor/service-visits/11/notes").contentType("application/json")
            .content("{\"expectedVersion\":1,\"note\":\"Inspection completed\",\"customerVisible\":true}"))
            .andExpect(status().isOk());
        verify(visits).updateStatus(eq(11L),any());
        verify(visits).recordNote(eq(11L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void viewOnlyEmployeeCannotMutateVisit(){
        try{
            mvc.perform(post("/api/v1/vendor/service-visits").contentType("application/json")
                .content("{\"caseType\":\"SERVICE_REQUEST\",\"caseId\":10,\"siteAddress\":\"Site\",\"scheduledAt\":\"2026-12-01T10:00:00Z\",\"assignedTo\":\"engineer@vendor.test\",\"reason\":\"Reason\"}"))
                .andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/vendor/service-visits/11/assignment").contentType("application/json")
                .content("{\"expectedVersion\":0,\"assignedTo\":\"engineer@vendor.test\",\"scheduledAt\":\"2026-12-01T10:00:00Z\",\"reason\":\"Reason\"}"))
                .andExpect(status().isForbidden());
        }catch(Exception ex){throw new AssertionError(ex);}
        verifyNoInteractions(visits);
    }

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerCanReadOnlyOwnVisitRoutes() throws Exception {
        when(visits.customerList(anyString())).thenReturn(List.of());
        mvc.perform(get("/api/v1/customer/service-visits")).andExpect(status().isOk());
        verify(visits).customerList(anyString());
    }

    @Test @WithMockUser(authorities={"SHIPMENT_VIEW"})
    void unrelatedPermissionCannotReadServiceVisits() throws Exception {
        mvc.perform(get("/api/v1/vendor/service-visits")).andExpect(status().isForbidden());
        verifyNoInteractions(visits);
    }
}
