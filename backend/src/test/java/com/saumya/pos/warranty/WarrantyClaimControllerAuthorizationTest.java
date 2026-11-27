package com.saumya.pos.warranty;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=WarrantyClaimController.class)
@Import(com.saumya.pos.AuthorizationTestSecurityConfiguration.class)
class WarrantyClaimControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean WarrantyClaimService claims;

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerCanSubmitAndReadClaimRoutes() throws Exception {
        when(claims.submit(anyString(),any())).thenReturn(null);
        when(claims.customerList(anyString())).thenReturn(List.of());
        mvc.perform(post("/api/v1/customer/warranty-claims").contentType("application/json")
            .content("{\"warrantyId\":9,\"subject\":\"Panel issue\",\"description\":\"Panel stopped producing\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/customer/warranty-claims")).andExpect(status().isOk());
        verify(claims).submit(anyString(),any());
        verify(claims).customerList(anyString());
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCanReadVendorClaims() throws Exception {
        when(claims.vendorList()).thenReturn(List.of());
        mvc.perform(get("/api/v1/vendor/warranty-claims")).andExpect(status().isOk());
        verify(claims).vendorList();
    }

    @Test @WithMockUser(authorities={"SERVICE_ASSIGN"})
    void serviceAssignCanAssignClaim() throws Exception {
        when(claims.assign(eq(9L),any())).thenReturn(null);
        mvc.perform(patch("/api/v1/vendor/warranty-claims/9/assignment").contentType("application/json")
            .content("{\"expectedVersion\":0,\"assignedTo\":\"engineer@test\",\"reason\":\"Workload allocation\"}"))
            .andExpect(status().isOk());
        verify(claims).assign(eq(9L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_UPDATE"})
    void serviceUpdateCanRecordInspection() throws Exception {
        when(claims.inspect(eq(9L),any())).thenReturn(null);
        mvc.perform(patch("/api/v1/vendor/warranty-claims/9/inspection").contentType("application/json")
            .content("{\"expectedVersion\":0,\"summary\":\"Visual inspection completed\",\"note\":\"Inspection recorded\",\"customerVisible\":true}"))
            .andExpect(status().isOk());
        verify(claims).inspect(eq(9L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_RESOLVE"})
    void serviceResolveCanDecideAndResolveClaim() throws Exception {
        when(claims.decide(eq(9L),any())).thenReturn(null);
        when(claims.resolve(eq(9L),any())).thenReturn(null);
        mvc.perform(patch("/api/v1/vendor/warranty-claims/9/decision").contentType("application/json")
            .content("{\"expectedVersion\":1,\"decision\":\"APPROVED\",\"resolutionType\":\"REPAIR\",\"reason\":\"Covered manufacturing fault\"}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/vendor/warranty-claims/9/resolution").contentType("application/json")
            .content("{\"expectedVersion\":2,\"note\":\"Repair completed and tested\"}"))
            .andExpect(status().isOk());
        verify(claims).decide(eq(9L),any());
        verify(claims).resolve(eq(9L),any());
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCannotApproveOrResolveClaim() throws Exception {
        mvc.perform(patch("/api/v1/vendor/warranty-claims/9/decision").contentType("application/json")
            .content("{\"expectedVersion\":1,\"decision\":\"REJECTED\",\"reason\":\"Not covered\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(claims);
    }

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerEvidenceUploadRequiresOwnClaimServicePath() throws Exception {
        MockMultipartFile file=new MockMultipartFile("file","proof.pdf","application/pdf",
            "%PDF-1.7 evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        when(claims.uploadCustomerEvidence(anyString(),eq(9L),anyString(),anyString(),any(byte[].class))).thenReturn(null);
        mvc.perform(multipart("/api/v1/customer/warranty-claims/9/evidence").file(file))
            .andExpect(status().isCreated());
        verify(claims).uploadCustomerEvidence(anyString(),eq(9L),anyString(),eq("application/pdf"),any(byte[].class));
    }

    @Test @WithMockUser(authorities={"SHIPMENT_VIEW"})
    void unrelatedGrantCannotReadClaims() throws Exception {
        mvc.perform(get("/api/v1/vendor/warranty-claims")).andExpect(status().isForbidden());
        verifyNoInteractions(claims);
    }
}
