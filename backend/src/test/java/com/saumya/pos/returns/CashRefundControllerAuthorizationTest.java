package com.saumya.pos.returns;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CashRefundController.class)
class CashRefundControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private CashRefundService service;
    
    // ==================== LIST ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void listSucceedsWithPosViewAndRefundView() throws Exception {
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", false,
            List.of(new CashRefundService.Line(1L, "Product A", 1, 
                BigDecimal.valueOf(45.00), BigDecimal.valueOf(5.00), BigDecimal.valueOf(50.00)))
        );
        
        when(service.list()).thenReturn(List.of(mockView));
        
        mvc.perform(get("/api/v1/vendor/pos/refunds"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("refund-123"));
        
        verify(service).list();
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Missing POS_REFUND_VIEW
    void listReturns403WithoutRefundView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).list();
    }
    
    @Test
    void listReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds"))
            .andExpect(status().isUnauthorized());
        
        verify(service, never()).list();
    }
    
    // ==================== GET BY ID ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void getSucceedsWithPosViewAndRefundView() throws Exception {
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", false,
            List.of(new CashRefundService.Line(1L, "Product A", 1,
                BigDecimal.valueOf(45.00), BigDecimal.valueOf(5.00), BigDecimal.valueOf(50.00)))
        );
        
        when(service.get(eq("refund-123"))).thenReturn(mockView);
        
        mvc.perform(get("/api/v1/vendor/pos/refunds/refund-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("refund-123"));
        
        verify(service).get(eq("refund-123"));
    }
    
    @Test
    @WithMockUser(authorities = {"POS_REFUND_VIEW"}) // Missing POS_VIEW
    void getReturns403WithoutPosView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/refund-123"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).get(any());
    }
    
    @Test
    void getReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/refund-123"))
            .andExpect(status().isUnauthorized());
        
        verify(service, never()).get(any());
    }
    
    // ==================== GET BY RETURN ID ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void byReturnSucceedsWithPosViewAndRefundView() throws Exception {
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", false,
            List.of(new CashRefundService.Line(1L, "Product A", 1,
                BigDecimal.valueOf(45.00), BigDecimal.valueOf(5.00), BigDecimal.valueOf(50.00)))
        );
        
        when(service.byReturn(eq("return-456"))).thenReturn(mockView);
        
        mvc.perform(get("/api/v1/vendor/pos/refunds/return/return-456"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.returnId").value("return-456"));
        
        verify(service).byReturn(eq("return-456"));
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Missing POS_REFUND_VIEW
    void byReturnReturns403WithoutRefundView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/return/return-456"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).byReturn(any());
    }
    
    @Test
    void byReturnReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/return/return-456"))
            .andExpect(status().isUnauthorized());
        
        verify(service, never()).byReturn(any());
    }
    
    // ==================== QUOTE ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_APPROVE", "POS_REFUND_CASH_RECORD"})
    void quoteSucceedsWithAllRequiredAuthorities() throws Exception {
        CashRefundService.Quote mockQuote = new CashRefundService.Quote(
            "return-456", "order-789", "1/RCP-001", "session-123",
            BigDecimal.valueOf(50.00), BigDecimal.valueOf(500.00), true,
            "abc123def456",
            List.of(new CashRefundService.Line(1L, "Product A", 1,
                BigDecimal.valueOf(45.00), BigDecimal.valueOf(5.00), BigDecimal.valueOf(50.00))),
            "Quote message"
        );
        
        when(service.quote(eq("return-456"), eq("session-123"))).thenReturn(mockQuote);
        
        mvc.perform(get("/api/v1/vendor/pos/refunds/quote/return-456")
            .param("sessionId", "session-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.returnId").value("return-456"));
        
        verify(service).quote(eq("return-456"), eq("session-123"));
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"}) // Missing POS_REFUND_APPROVE and POS_REFUND_CASH_RECORD
    void quoteReturns403WithoutApproveAndCashRecordAuthorities() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/quote/return-456")
            .param("sessionId", "session-123"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).quote(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_APPROVE"}) // Missing POS_REFUND_CASH_RECORD
    void quoteReturns403WithoutCashRecordAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/quote/return-456")
            .param("sessionId", "session-123"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).quote(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_CASH_RECORD"}) // Missing POS_REFUND_APPROVE
    void quoteReturns403WithoutApproveAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/quote/return-456")
            .param("sessionId", "session-123"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).quote(any(), any());
    }
    
    @Test
    void quoteReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/refunds/quote/return-456")
            .param("sessionId", "session-123"))
            .andExpect(status().isUnauthorized());
        
        verify(service, never()).quote(any(), any());
    }
    
    // ==================== PAY ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_APPROVE", "POS_REFUND_CASH_RECORD"})
    void paySucceedsWithAllRequiredAuthorities() throws Exception {
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", false,
            List.of(new CashRefundService.Line(1L, "Product A", 1,
                BigDecimal.valueOf(45.00), BigDecimal.valueOf(5.00), BigDecimal.valueOf(50.00)))
        );
        
        when(service.pay(eq("idempotency-key-123"), any())).thenReturn(mockView);
        
        mvc.perform(post("/api/v1/vendor/pos/refunds")
            .header("Idempotency-Key", "idempotency-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"returnId\":\"return-456\",\"sessionId\":\"session-123\"," +
                "\"reason\":\"Damaged product\",\"quoteHash\":\"abc123def456789012345678901234567890123456789012345678901234\"," +
                "\"confirmation\":\"CASH_HANDED_TO_BUYER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("refund-123"));
        
        verify(service).pay(eq("idempotency-key-123"), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"}) // Missing POS_REFUND_APPROVE and POS_REFUND_CASH_RECORD
    void payReturns403WithoutApproveAndCashRecordAuthorities() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds")
            .header("Idempotency-Key", "idempotency-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"returnId\":\"return-456\",\"sessionId\":\"session-123\"," +
                "\"reason\":\"Damaged product\",\"quoteHash\":\"abc123def456789012345678901234567890123456789012345678901234\"," +
                "\"confirmation\":\"CASH_HANDED_TO_BUYER\"}"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).pay(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_APPROVE"}) // Missing POS_REFUND_CASH_RECORD
    void payReturns403WithoutCashRecordAuthority() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds")
            .header("Idempotency-Key", "idempotency-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"returnId\":\"return-456\",\"sessionId\":\"session-123\"," +
                "\"reason\":\"Damaged product\",\"quoteHash\":\"abc123def456789012345678901234567890123456789012345678901234\"," +
                "\"confirmation\":\"CASH_HANDED_TO_BUYER\"}"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).pay(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_CASH_RECORD"}) // Missing POS_REFUND_APPROVE
    void payReturns403WithoutApproveAuthority() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds")
            .header("Idempotency-Key", "idempotency-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"returnId\":\"return-456\",\"sessionId\":\"session-123\"," +
                "\"reason\":\"Damaged product\",\"quoteHash\":\"abc123def456789012345678901234567890123456789012345678901234\"," +
                "\"confirmation\":\"CASH_HANDED_TO_BUYER\"}"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).pay(any(), any());
    }
    
    @Test
    void payReturns401WithoutAuthentication() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds")
            .header("Idempotency-Key", "idempotency-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"returnId\":\"return-456\",\"sessionId\":\"session-123\"," +
                "\"reason\":\"Damaged product\",\"quoteHash\":\"abc123def456789012345678901234567890123456789012345678901234\"," +
                "\"confirmation\":\"CASH_HANDED_TO_BUYER\"}"))
            .andExpect(status().isUnauthorized());
        
        verify(service, never()).pay(any(), any());
    }
    
    // ==================== COPY ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_PRINT"})
    void copySucceedsWithAllRequiredAuthorities() throws Exception {
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", true, // copy = true
            List.of(new CashRefundService.Line(1L, "Product A", 1,
                BigDecimal.valueOf(45.00), BigDecimal.valueOf(5.00), BigDecimal.valueOf(50.00)))
        );
        
        when(service.copy(eq("refund-123"))).thenReturn(mockView);
        
        mvc.perform(post("/api/v1/vendor/pos/refunds/refund-123/copy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("refund-123"))
            .andExpect(jsonPath("$.copy").value(true));
        
        verify(service).copy(eq("refund-123"));
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"}) // Missing POS_REFUND_PRINT
    void copyReturns403WithoutPrintAuthority() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds/refund-123/copy"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).copy(any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_PRINT"}) // Missing POS_REFUND_VIEW
    void copyReturns403WithoutRefundView() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds/refund-123/copy"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).copy(any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_REFUND_VIEW", "POS_REFUND_PRINT"}) // Missing POS_VIEW
    void copyReturns403WithoutPosView() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds/refund-123/copy"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).copy(any());
    }
    
    @Test
    void copyReturns401WithoutAuthentication() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/refunds/refund-123/copy"))
            .andExpect(status().isUnauthorized());
        
        verify(service, never()).copy(any());
    }
    
    // ==================== VIEW ≠ MUTATE TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void refundViewDoesNotGrantQuoteAccess() throws Exception {
        // READ operations allowed
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", false,
            List.of()
        );
        
        when(service.get(eq("refund-123"))).thenReturn(mockView);
        
        mvc.perform(get("/api/v1/vendor/pos/refunds/refund-123"))
            .andExpect(status().isOk());
        
        // QUOTE operation blocked without additional authorities
        mvc.perform(get("/api/v1/vendor/pos/refunds/quote/return-456")
            .param("sessionId", "session-123"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).quote(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void refundViewDoesNotGrantPayAccess() throws Exception {
        // READ operations allowed
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", false,
            List.of()
        );
        
        when(service.list()).thenReturn(List.of(mockView));
        
        mvc.perform(get("/api/v1/vendor/pos/refunds"))
            .andExpect(status().isOk());
        
        // PAY operation blocked without additional authorities
        mvc.perform(post("/api/v1/vendor/pos/refunds")
            .header("Idempotency-Key", "idempotency-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"returnId\":\"return-456\",\"sessionId\":\"session-123\"," +
                "\"reason\":\"Damaged product\",\"quoteHash\":\"abc123def456789012345678901234567890123456789012345678901234\"," +
                "\"confirmation\":\"CASH_HANDED_TO_BUYER\"}"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).pay(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REFUND_VIEW"})
    void refundViewDoesNotGrantPrintAccess() throws Exception {
        // READ operations allowed
        CashRefundService.View mockView = new CashRefundService.View(
            "refund-123", "return-456", "order-789", "1/RCP-001",
            "1", "cashier@vendor.com", "session-123", BigDecimal.valueOf(50.00),
            "Damaged product", Instant.now(), "CASH", "PAID", false,
            List.of()
        );
        
        when(service.byReturn(eq("return-456"))).thenReturn(mockView);
        
        mvc.perform(get("/api/v1/vendor/pos/refunds/return/return-456"))
            .andExpect(status().isOk());
        
        // COPY/PRINT operation blocked without POS_REFUND_PRINT authority
        mvc.perform(post("/api/v1/vendor/pos/refunds/refund-123/copy"))
            .andExpect(status().isForbidden());
        
        verify(service, never()).copy(any());
    }
    
    // ==================== COMPREHENSIVE AUTHORITY VALIDATION ====================
    
    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        // List
        mvc.perform(get("/api/v1/vendor/pos/refunds"))
            .andExpect(status().isUnauthorized());
        
        // Get by ID
        mvc.perform(get("/api/v1/vendor/pos/refunds/refund-123"))
            .andExpect(status().isUnauthorized());
        
        // Get by Return ID
        mvc.perform(get("/api/v1/vendor/pos/refunds/return/return-456"))
            .andExpect(status().isUnauthorized());
        
        // Quote
        mvc.perform(get("/api/v1/vendor/pos/refunds/quote/return-456")
            .param("sessionId", "session-123"))
            .andExpect(status().isUnauthorized());
        
        // Pay
        mvc.perform(post("/api/v1/vendor/pos/refunds")
            .header("Idempotency-Key", "idempotency-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"returnId\":\"return-456\",\"sessionId\":\"session-123\"," +
                "\"reason\":\"Damaged product\",\"quoteHash\":\"abc123def456789012345678901234567890123456789012345678901234\"," +
                "\"confirmation\":\"CASH_HANDED_TO_BUYER\"}"))
            .andExpect(status().isUnauthorized());
        
        // Copy
        mvc.perform(post("/api/v1/vendor/pos/refunds/refund-123/copy"))
            .andExpect(status().isUnauthorized());
        
        // Verify no service methods were called
        verifyNoInteractions(service);
    }
}
