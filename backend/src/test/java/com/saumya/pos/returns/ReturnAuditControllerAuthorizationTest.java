package com.saumya.pos.returns;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReturnAuditController.class)
class ReturnAuditControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private ReturnAuditService returnAuditService;
    
    // ==================== AUTHORIZED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_AUDIT_VIEW"})
    void historySucceedsWithAllRequiredAuthorities() throws Exception {
        when(returnAuditService.history(anyString(), anyInt(), anyInt()))
            .thenReturn(Map.of("events", List.of()));
        
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-123/events")
            .param("page", "0")
            .param("size", "12"))
            .andExpect(status().isOk());
        
        verify(returnAuditService).history("ret-123", 0, 12);
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_AUDIT_VIEW"})
    void historySucceedsWithDefaultPagination() throws Exception {
        when(returnAuditService.history(anyString(), anyInt(), anyInt()))
            .thenReturn(Map.of("events", List.of()));
        
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-456/events"))
            .andExpect(status().isOk());
        
        verify(returnAuditService).history("ret-456", 0, 12);
    }
    
    // ==================== MISSING PERMISSION TESTS (403) ====================
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"}) // No POS authorities
    void historyReturns403WithoutAnyPosAuthorities() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-123/events"))
            .andExpect(status().isForbidden());
        
        verify(returnAuditService, never()).history(anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Missing POS_RETURN_VIEW and POS_RETURN_AUDIT_VIEW
    void historyReturns403WithOnlyPosView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-123/events"))
            .andExpect(status().isForbidden());
        
        verify(returnAuditService, never()).history(anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"}) // Missing POS_RETURN_AUDIT_VIEW
    void historyReturns403WithoutAuditViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-123/events"))
            .andExpect(status().isForbidden());
        
        verify(returnAuditService, never()).history(anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_AUDIT_VIEW"}) // Missing POS_RETURN_VIEW
    void historyReturns403WithoutReturnViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-123/events"))
            .andExpect(status().isForbidden());
        
        verify(returnAuditService, never()).history(anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_RETURN_VIEW", "POS_RETURN_AUDIT_VIEW"}) // Missing POS_VIEW
    void historyReturns403WithoutPosViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-123/events"))
            .andExpect(status().isForbidden());
        
        verify(returnAuditService, never()).history(anyString(), anyInt(), anyInt());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TEST ====================
    
    @Test
    void historyRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/ret-123/events"))
            .andExpect(status().isUnauthorized());
        
        verify(returnAuditService, never()).history(anyString(), anyInt(), anyInt());
    }
}
