package com.saumya.pos.service;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=ServiceSlaEscalationController.class)
@Import(com.saumya.pos.AuthorizationTestSecurityConfiguration.class)
class ServiceSlaEscalationControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean ServiceSlaEscalationService service;

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCanReadPoliciesAndEscalationQueue() throws Exception {
        when(service.policies()).thenReturn(List.of());
        when(service.queue()).thenReturn(List.of());
        mvc.perform(get("/api/v1/vendor/service-sla/escalation-policies")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vendor/service-sla/escalations")).andExpect(status().isOk());
        verify(service).policies();
        verify(service).queue();
    }

    @Test @WithMockUser(authorities={"SERVICE_ASSIGN"})
    void serviceAssignCanSaveAnEscalationPolicy() throws Exception {
        when(service.savePolicy(any())).thenReturn(new ServiceSlaEscalationService.Policy(
            1L,"SERVICE_REQUEST","HIGH","RESPONSE",1,30,"SERVICE_MANAGER",true,0));
        mvc.perform(put("/api/v1/vendor/service-sla/escalation-policies")
            .contentType("application/json")
            .content("{\"caseType\":\"SERVICE_REQUEST\",\"priority\":\"HIGH\",\"breachType\":\"RESPONSE\",\"escalationLevel\":1,\"afterMinutes\":30,\"recipientRoleKey\":\"SERVICE_MANAGER\",\"enabled\":true}"))
            .andExpect(status().isOk());
        verify(service).savePolicy(any());
    }

    @Test @WithMockUser(authorities={"SERVICE_UPDATE"})
    void serviceUpdateCanAcknowledgeEscalation() throws Exception {
        when(service.acknowledge(eq(9L),any())).thenReturn(new ServiceSlaEscalationService.Escalation(
            9L,81L,"SERVICE_REQUEST",21L,"RESPONSE",1,"SERVICE_MANAGER","ACKNOWLEDGED",
            Instant.parse("2026-11-28T00:00:00Z"),"manager@vendor.test",
            Instant.parse("2026-11-28T00:05:00Z"),"I have taken ownership"));
        mvc.perform(post("/api/v1/vendor/service-sla/escalations/9/acknowledge")
            .contentType("application/json").content("{\"note\":\"I have taken ownership\"}"))
            .andExpect(status().isOk());
        verify(service).acknowledge(eq(9L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCannotAcknowledgeEscalation() throws Exception {
        mvc.perform(post("/api/v1/vendor/service-sla/escalations/9/acknowledge")
            .contentType("application/json").content("{\"note\":\"Taking ownership\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"SHIPMENT_VIEW"})
    void unrelatedPermissionCannotAccessEscalationQueue() throws Exception {
        mvc.perform(get("/api/v1/vendor/service-sla/escalations")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"SERVICE_ASSIGN"})
    void invalidEscalationLevelIsRejectedByRequestValidation() throws Exception {
        mvc.perform(put("/api/v1/vendor/service-sla/escalation-policies")
            .contentType("application/json")
            .content("{\"caseType\":\"SERVICE_REQUEST\",\"priority\":\"HIGH\",\"breachType\":\"RESPONSE\",\"escalationLevel\":8,\"afterMinutes\":30,\"recipientRoleKey\":\"SERVICE_MANAGER\",\"enabled\":true}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
