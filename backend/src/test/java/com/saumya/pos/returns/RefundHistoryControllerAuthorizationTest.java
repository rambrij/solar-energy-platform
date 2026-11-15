package com.saumya.pos.returns;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RefundHistoryController.class)
class RefundHistoryControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private RefundHistoryService refundHistoryService;
    
    // ==================== AUTHORIZED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void historySucceedsWithRequiredAuthorities() throws Exception {
        when(refundHistoryService.history(any(), any(), anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn(List.of());
        
        mvc.perform(get("/api/v1/vendor/pos/refunds/history")
            .param("page", "0")
            .param("size", "24"))
            .andExpect(status().isOk());
        
        verify(refundHistoryService).history(any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void historySucceedsWithDateRangeParameters() throws Exception {
        when(refundHistoryService.history(any(), any(), anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn(List.of());
        
        mvc.perform(get("/api/v1/vendor/pos/refunds/history")
            .param("from", "2024-01-01")
            .param("to", "2024-01-31")
            .param("reference", "REF123")
            .param("returnId", "RET456")
            .param("page", "0")
            .param("size", "24"))
            .andExpect(status().isOk());
        
        verify(refundHistoryService).history(any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }
    
    // ==================== MISSING PERMISSION TESTS (403) ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Has POS_VIEW but not POS_REFUND_VIEW
    void historyReturns403WithoutPosRefundView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/history")
            .param("page", "0")
            .param("size", "24"))
            .andExpect(status().isForbidden());
        
        verify(refundHistoryService, never()).history(any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_REFUND_VIEW"}) // Has POS_REFUND_VIEW but not POS_VIEW
    void historyReturns403WithoutPosView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/history")
            .param("page", "0")
            .param("size", "24"))
            .andExpect(status().isForbidden());
        
        verify(refundHistoryService, never()).history(any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"}) // Neither POS_VIEW nor POS_REFUND_VIEW
    void historyReturns403WithUnrelatedAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/history")
            .param("page", "0")
            .param("size", "24"))
            .andExpect(status().isForbidden());
        
        verify(refundHistoryService, never()).history(any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TEST ====================
    
    @Test
    void historyRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/history")
            .param("page", "0")
            .param("size", "24"))
            .andExpect(status().isUnauthorized());
        
        verify(refundHistoryService, never()).history(any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }
}
