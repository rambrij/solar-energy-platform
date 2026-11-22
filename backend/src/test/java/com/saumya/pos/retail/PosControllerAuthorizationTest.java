package com.saumya.pos.retail;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;

import com.saumya.pos.security.AuthorizationContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PosController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class PosControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private PosService posService;
    
    // ==================== AUTHORIZED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"})
    void getProductsSucceedsWithPosView() throws Exception {
        when(posService.scope()).thenReturn(new PosService.Scope(1L, 10L, "cashier@vendor.com"));
        when(posService.products(any())).thenReturn(List.of());
        
        mvc.perform(get("/api/v1/vendor/pos/products"))
            .andExpect(status().isOk());
        
        verify(posService).products(any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_SESSION_OPEN"})
    void openSessionSucceedsWithSessionOpenPermission() throws Exception {
        when(posService.scope()).thenReturn(new PosService.Scope(1L, 10L, "cashier@vendor.com"));
        when(posService.open(any(), any())).thenReturn(Map.of("id", "session-123"));
        
        mvc.perform(post("/api/v1/vendor/pos/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"registerId\": 1, \"openingFloat\": 100.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("session-123"));
    }
    
    @Test
    @WithMockUser(authorities = {"POS_SESSION_CLOSE"})
    void closeSessionSucceedsWithSessionClosePermission() throws Exception {
        when(posService.scope()).thenReturn(new PosService.Scope(1L, 10L, "cashier@vendor.com"));
        when(posService.close(any(), any(), any(), any(), any())).thenReturn(
            new PosService.CloseResult("session-123", 1L, "cashier@vendor.com", 
                "CLOSED", "req-1", BigDecimal.valueOf(500), BigDecimal.valueOf(500), 
                BigDecimal.ZERO, null, null, null)
        );
        
        mvc.perform(post("/api/v1/vendor/pos/sessions/session-123/close")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"countedCash\": 500.00, \"reason\": null, \"requestId\": \"550e8400-e29b-41d4-a716-446655440000\"}"))
            .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_SHIFT_VARIANCE_APPROVE"})
    void approveVarianceSucceedsWithApprovalPermission() throws Exception {
        when(posService.scope()).thenReturn(new PosService.Scope(1L, 10L, "supervisor@vendor.com"));
        when(posService.reviewVariance(any(), any(), any(), any(), any()))
            .thenReturn(Map.of("decision", "APPROVE"));
        
        mvc.perform(post("/api/v1/vendor/pos/sessions/session-123/approve-variance")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\": \"req-1\", \"reason\": \"Approved\"}")
        )
            .andExpect(status().isOk());
    }
    
    // ==================== MISSING PERMISSION TESTS (403) ====================
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"}) // Customer, not POS user
    void getProductsReturns403WithoutPosView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/products"))
            .andExpect(status().isForbidden());
        
        verify(posService, never()).products(any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Has VIEW but not SESSION_OPEN
    void openSessionReturns403WithoutSessionOpenPermission() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"registerId\": 1, \"openingFloat\": 100.00}"))
            .andExpect(status().isForbidden());
        
        verify(posService, never()).open(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Has VIEW but not SESSION_CLOSE
    void closeSessionReturns403WithoutSessionClosePermission() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/sessions/session-123/close")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"countedCash\": 500.00, \"reason\": null, \"requestId\": \"550e8400-e29b-41d4-a716-446655440000\"}"))
            .andExpect(status().isForbidden());
        
        verify(posService, never()).close(any(), any(), any(), any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_SALE_CREATE"}) // Cashier without approval permission
    void approveVarianceReturns403WithoutApprovalPermission() throws Exception {
        mvc.perform(post("/api/v1/vendor/pos/sessions/session-123/approve-variance")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\": \"req-1\", \"reason\": \"Approved\"}"))
            .andExpect(status().isForbidden());
        
        verify(posService, never()).reviewVariance(any(), any(), any(), any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Has VIEW but not SHIFT_VARIANCE_APPROVE
    void pendingVariancesReturns403WithoutApprovalPermission() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/sessions/pending-variances"))
            .andExpect(status().isForbidden());
        
        verify(posService, never()).pendingVariances(any());
    }
    
    // ==================== VIEW ≠ MUTATE TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_REPORT_VIEW"})
    void reportViewDoesNotGrantSessionClose() throws Exception {
        when(posService.scope()).thenReturn(new PosService.Scope(1L, 10L, "cashier@vendor.com"));
        when(posService.reconciliation(any(), any())).thenReturn(
            new PosService.Reconciliation("session-123", "OPEN", BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(100), null, null, List.of(), BigDecimal.ZERO)
        );
        
        // READ allowed
        mvc.perform(get("/api/v1/vendor/pos/sessions/session-123/reconciliation"))
            .andExpect(status().isOk());
        
        // WRITE blocked
        mvc.perform(post("/api/v1/vendor/pos/sessions/session-123/close")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"countedCash\": 500.00, \"reason\": null, \"requestId\": \"550e8400-e29b-41d4-a716-446655440000\"}"))
            .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"})
    void posViewDoesNotGrantSessionResume() throws Exception {
        // Has POS_VIEW but not POS_SESSION_OPEN (required for resume)
        mvc.perform(post("/api/v1/vendor/pos/sessions/session-123/resume")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isForbidden());
        
        verify(posService, never()).resume(any(), any());
    }
    
    // ==================== CROSS-TENANT ACCESS TEST ====================
    
    @Test
    @WithMockUser(username = "cashierA@vendorA.com", authorities = {"POS_VIEW"})
    void cannotAccessOtherTenantSessions() throws Exception {
        // Service layer should enforce tenant isolation
        when(posService.scope()).thenReturn(new PosService.Scope(1L, 10L, "cashierA@vendorA.com"));
        when(posService.sessionDetails(any(), any()))
            .thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Resource unavailable" // Cross-tenant resources appear as NOT_FOUND
            ));
        
        mvc.perform(get("/api/v1/vendor/pos/sessions/vendor-b-session"))
            .andExpect(status().isNotFound());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TEST ====================
    
    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/products"))
            .andExpect(status().isUnauthorized());
        
        mvc.perform(post("/api/v1/vendor/pos/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"registerId\": 1, \"openingFloat\": 100.00}"))
            .andExpect(status().isUnauthorized());
        
        mvc.perform(get("/api/v1/vendor/pos/sessions/pending-variances"))
            .andExpect(status().isUnauthorized());
    }
}
