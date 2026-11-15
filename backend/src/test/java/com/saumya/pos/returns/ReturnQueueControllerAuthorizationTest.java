package com.saumya.pos.returns;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;


import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReturnQueueController.class)
class ReturnQueueControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private ReturnQueueService returnQueueService;
    
    // ==================== AUTHORIZED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"})
    void queueSucceedsWithRequiredAuthorities() throws Exception {
        when(returnQueueService.queue(anyString(), any(), any(), anyString(), anyInt(), anyInt()))
            .thenReturn(new ReturnQueueService.Queue(1, "REQUESTED", null, null, "", 0, 12, 0, 0, List.of()));
        
        mvc.perform(get("/api/v1/vendor/pos/returns/queue")
            .param("state", "REQUESTED")
            .param("page", "0")
            .param("size", "12"))
            .andExpect(status().isOk());
        
        verify(returnQueueService).queue("REQUESTED", null, null, "", 0, 12);
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"})
    void queueSucceedsWithDefaultParameters() throws Exception {
        when(returnQueueService.queue(anyString(), any(), any(), anyString(), anyInt(), anyInt()))
            .thenReturn(new ReturnQueueService.Queue(1, "REQUESTED", null, null, "", 0, 12, 0, 0, List.of()));
        
        mvc.perform(get("/api/v1/vendor/pos/returns/queue"))
            .andExpect(status().isOk());
        
        verify(returnQueueService).queue("REQUESTED", null, null, "", 0, 12);
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"})
    void queueSucceedsWithDateFilters() throws Exception {
        when(returnQueueService.queue(anyString(), any(), any(), anyString(), anyInt(), anyInt()))
            .thenReturn(new ReturnQueueService.Queue(1, "REQUESTED", null, null, "", 0, 12, 0, 0, List.of()));
        
        mvc.perform(get("/api/v1/vendor/pos/returns/queue")
            .param("state", "APPROVED")
            .param("from", "2024-01-01")
            .param("to", "2024-01-31")
            .param("reference", "REF-123")
            .param("page", "1")
            .param("size", "20"))
            .andExpect(status().isOk());
        
        verify(returnQueueService).queue(
            eq("APPROVED"), 
            eq(LocalDate.of(2024, 1, 1)), 
            eq(LocalDate.of(2024, 1, 31)), 
            eq("REF-123"), 
            eq(1), 
            eq(20)
        );
    }
    
    // ==================== MISSING PERMISSION TESTS (403) ====================
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"}) // No POS authorities
    void queueReturns403WithoutAnyPosAuthorities() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/queue"))
            .andExpect(status().isForbidden());
        
        verify(returnQueueService, never()).queue(anyString(), any(), any(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Missing POS_RETURN_VIEW
    void queueReturns403WithOnlyPosView() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/queue"))
            .andExpect(status().isForbidden());
        
        verify(returnQueueService, never()).queue(anyString(), any(), any(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_RETURN_VIEW"}) // Missing POS_VIEW
    void queueReturns403WithoutPosViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/queue"))
            .andExpect(status().isForbidden());
        
        verify(returnQueueService, never()).queue(anyString(), any(), any(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_SALE_CREATE"}) // Has POS_VIEW but wrong other permission
    void queueReturns403WithoutReturnViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/queue"))
            .andExpect(status().isForbidden());
        
        verify(returnQueueService, never()).queue(anyString(), any(), any(), anyString(), anyInt(), anyInt());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TEST ====================
    
    @Test
    void queueRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/queue"))
            .andExpect(status().isUnauthorized());
        
        verify(returnQueueService, never()).queue(anyString(), any(), any(), anyString(), anyInt(), anyInt());
    }
}
