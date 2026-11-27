package com.saumya.pos.warranty;

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

@WebMvcTest(controllers=WarrantyController.class)
@Import(com.saumya.pos.AuthorizationTestSecurityConfiguration.class)
class WarrantyControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean WarrantyService warranties;

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void serviceViewCanReadVendorWarrantyRegister() throws Exception {
        when(warranties.vendorList()).thenReturn(List.of());
        mvc.perform(get("/api/v1/vendor/warranties")).andExpect(status().isOk());
        verify(warranties).vendorList();
    }

    @Test @WithMockUser(authorities={"SERVICE_VIEW"})
    void readOnlyServiceGrantCannotRegisterWarranty() throws Exception {
        mvc.perform(post("/api/v1/vendor/warranties").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"productId\":9,\"coverageStartDate\":\"2026-11-27\",\"coverageEndDate\":\"2027-11-27\",\"termsSummary\":\"Standard warranty\",\"reason\":\"Vendor registration\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(warranties);
    }

    @Test @WithMockUser(authorities={"SERVICE_CREATE"})
    void serviceCreateCanRegisterWarranty() throws Exception {
        when(warranties.register(any())).thenReturn(null);
        mvc.perform(post("/api/v1/vendor/warranties").contentType("application/json")
            .content("{\"orderId\":\"order-1\",\"productId\":9,\"coverageStartDate\":\"2026-11-27\",\"coverageEndDate\":\"2027-11-27\",\"termsSummary\":\"Standard warranty\",\"reason\":\"Vendor registration\"}"))
            .andExpect(status().isCreated());
        verify(warranties).register(any());
    }

    @Test @WithMockUser(authorities={"CUSTOMER"})
    void customerCanReadOwnWarrantyEndpoints() throws Exception {
        when(warranties.customerList(anyString())).thenReturn(List.of());
        mvc.perform(get("/api/v1/customer/warranties")).andExpect(status().isOk());
        verify(warranties).customerList(anyString());
    }

    @Test @WithMockUser(authorities={"SHIPMENT_VIEW"})
    void unrelatedVendorGrantCannotReadWarrantyRegister() throws Exception {
        mvc.perform(get("/api/v1/vendor/warranties")).andExpect(status().isForbidden());
        verifyNoInteractions(warranties);
    }
}
