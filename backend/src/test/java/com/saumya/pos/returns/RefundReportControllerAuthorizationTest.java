package com.saumya.pos.returns;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RefundReportController.class)
class RefundReportControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private RefundReportService refundReportService;
    
    // ==================== AUTHORIZED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REPORT_VIEW", "POS_REFUND_VIEW"})
    void reportSucceedsWithAllRequiredAuthorities() throws Exception {
        when(refundReportService.report(any(), any())).thenReturn(new RefundReportService.Report(1, LocalDate.now(), LocalDate.now(), Instant.now(), new RefundReportService.Totals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), List.of(), List.of()));
        
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds"))
            .andExpect(status().isOk());
        
        verify(refundReportService).report(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REPORT_VIEW", "POS_REFUND_VIEW"})
    void reportSucceedsWithDateRangeParameters() throws Exception {
        when(refundReportService.report(any(), any())).thenReturn(new RefundReportService.Report(1, LocalDate.now(), LocalDate.now(), Instant.now(), new RefundReportService.Totals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), List.of(), List.of()));
        
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds")
            .param("from", "2024-01-01")
            .param("to", "2024-01-31"))
            .andExpect(status().isOk());
        
        verify(refundReportService).report(any(), any());
    }
    
    // ==================== MISSING PERMISSION TESTS (403) ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REPORT_VIEW"}) // Missing POS_REFUND_VIEW
    void reportReturns403WithoutPosRefundView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds"))
            .andExpect(status().isForbidden());
        
        verify(refundReportService, never()).report(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"}) // Missing POS_REPORT_VIEW
    void reportReturns403WithoutPosReportView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds"))
            .andExpect(status().isForbidden());
        
        verify(refundReportService, never()).report(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_REPORT_VIEW", "POS_REFUND_VIEW"}) // Missing POS_VIEW
    void reportReturns403WithoutPosView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds"))
            .andExpect(status().isForbidden());
        
        verify(refundReportService, never()).report(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Only POS_VIEW, missing both POS_REPORT_VIEW and POS_REFUND_VIEW
    void reportReturns403WithOnlyPosView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds"))
            .andExpect(status().isForbidden());
        
        verify(refundReportService, never()).report(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"}) // Unrelated authority
    void reportReturns403WithUnrelatedAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds"))
            .andExpect(status().isForbidden());
        
        verify(refundReportService, never()).report(any(), any());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TEST ====================
    
    @Test
    void reportRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/reports/cash-refunds"))
            .andExpect(status().isUnauthorized());
        
        verify(refundReportService, never()).report(any(), any());
    }
}
