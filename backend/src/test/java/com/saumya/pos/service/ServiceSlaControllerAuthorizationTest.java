package com.saumya.pos.service;

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

@WebMvcTest(controllers=ServiceSlaController.class)
@Import(com.saumya.pos.AuthorizationTestSecurityConfiguration.class)
class ServiceSlaControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean ServiceSlaService sla;

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCanReadSlaDashboardAndPolicies() throws Exception {
        when(sla.dashboard()).thenReturn(List.of());
        when(sla.policies()).thenReturn(List.of());
        mvc.perform(get("/api/v1/vendor/service-sla")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vendor/service-sla/policies")).andExpect(status().isOk());
        verify(sla).dashboard();
        verify(sla).policies();
    }

    @Test @WithMockUser(authorities={"SERVICE_ASSIGN"})
    void serviceAssignCanConfigureTenantSlaPolicy() throws Exception {
        when(sla.updatePolicy(any())).thenReturn(new ServiceSlaService.Policy(
            "SERVICE_REQUEST","HIGH",60,240,true,1,null,"manager@vendor.test"));
        mvc.perform(put("/api/v1/vendor/service-sla/policies").contentType("application/json")
            .content("{\"caseType\":\"SERVICE_REQUEST\",\"priority\":\"HIGH\",\"responseTargetMinutes\":60,\"resolutionTargetMinutes\":240,\"reason\":\"Service policy approved\"}"))
            .andExpect(status().isOk());
        verify(sla).updatePolicy(any());
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCannotChangeSlaPolicy() throws Exception {
        mvc.perform(put("/api/v1/vendor/service-sla/policies").contentType("application/json")
            .content("{\"caseType\":\"SERVICE_REQUEST\",\"priority\":\"HIGH\",\"responseTargetMinutes\":60,\"resolutionTargetMinutes\":240,\"reason\":\"Policy\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(sla);
    }

    @Test @WithMockUser(authorities={"SHIPMENT_VIEW"})
    void unrelatedPermissionCannotReadSla() throws Exception {
        mvc.perform(get("/api/v1/vendor/service-sla")).andExpect(status().isForbidden());
        verifyNoInteractions(sla);
    }
}
