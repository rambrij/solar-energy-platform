package com.saumya.pos.returns;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReturnController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class ReturnControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private ReturnService returnService;
    
    // ==================== LIST ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"})
    void listReturnsSucceedsWithRequiredAuthorities() throws Exception {
        when(returnService.list(any())).thenReturn(List.of(
            new ReturnService.View(
                "return-123",
                "order-456",
                "REQUESTED",
                "cashier@vendor.com",
                "Defective product",
                "ORIGINAL_RECEIPT",
                "Customer provided original receipt",
                Instant.now(),
                null,
                null,
                null,
                "NOT_INITIATED",
                List.of()
            )
        ));
        
        mvc.perform(get("/api/v1/vendor/pos/returns?state=REQUESTED"))
            .andExpect(status().isOk());
        
        verify(returnService).list("REQUESTED");
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Missing POS_RETURN_VIEW
    void listReturnsReturns403WithoutReturnViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns?state=REQUESTED"))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).list(any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_RETURN_VIEW"}) // Missing POS_VIEW
    void listReturnsReturns403WithoutPosViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns?state=REQUESTED"))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).list(any());
    }
    
    @Test
    void listReturnsReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns?state=REQUESTED"))
            .andExpect(status().isUnauthorized());
        
        verify(returnService, never()).list(any());
    }
    
    // ==================== GET ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"})
    void getReturnSucceedsWithRequiredAuthorities() throws Exception {
        when(returnService.get(any())).thenReturn(
            new ReturnService.View(
                "return-123",
                "order-456",
                "REQUESTED",
                "cashier@vendor.com",
                "Defective product",
                "ORIGINAL_RECEIPT",
                "Customer provided original receipt",
                Instant.now(),
                null,
                null,
                null,
                "NOT_INITIATED",
                List.of(new ReturnService.Line(1L, "Product A", 2, null, null, null))
            )
        );
        
        mvc.perform(get("/api/v1/vendor/pos/returns/return-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("return-123"));
        
        verify(returnService).get("return-123");
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW"}) // Missing POS_RETURN_VIEW
    void getReturnReturns403WithoutReturnViewAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/return-123"))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).get(any());
    }
    
    @Test
    void getReturnReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/return-123"))
            .andExpect(status().isUnauthorized());
        
        verify(returnService, never()).get(any());
    }
    
    // ==================== ELIGIBILITY ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_CREATE"})
    void checkEligibilitySucceedsWithRequiredAuthorities() throws Exception {
        when(returnService.eligibility(any())).thenReturn(
            new ReturnService.Eligibility(
                "order-456",
                Instant.now().minusSeconds(86400),
                30,
                Instant.now().plusSeconds(86400 * 29),
                true,
                "Receipt/item proof and independent review are required. No refund will be initiated.",
                List.of(new ReturnService.AvailableLine(1L, "Product A", 5, 5))
            )
        );
        
        mvc.perform(get("/api/v1/vendor/pos/returns/sale/order-456"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("order-456"))
            .andExpect(jsonPath("$.eligible").value(true));
        
        verify(returnService).eligibility("order-456");
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"}) // Missing POS_RETURN_CREATE
    void checkEligibilityReturns403WithoutCreateAuthority() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/sale/order-456"))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).eligibility(any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_RETURN_CREATE"}) // Missing POS_VIEW and POS_RETURN_VIEW
    void checkEligibilityReturns403WithoutBaseAuthorities() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/sale/order-456"))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).eligibility(any());
    }
    
    @Test
    void checkEligibilityReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/returns/sale/order-456"))
            .andExpect(status().isUnauthorized());
        
        verify(returnService, never()).eligibility(any());
    }
    
    // ==================== CREATE ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_CREATE"})
    void createReturnSucceedsWithRequiredAuthorities() throws Exception {
        when(returnService.create(any(), any())).thenReturn(
            new ReturnService.View(
                "return-789",
                "order-456",
                "REQUESTED",
                "cashier@vendor.com",
                "Defective product",
                "ORIGINAL_RECEIPT",
                "Customer provided original receipt",
                Instant.now(),
                null,
                null,
                null,
                "NOT_INITIATED",
                List.of(new ReturnService.Line(1L, "Product A", 2, null, null, null))
            )
        );
        
        String requestBody = """
            {
            "orderId": "order-456",
            "sessionId": "session-123",
            "items": [{"productId": 1, "quantity": 2}],
            "reason": "Defective product",
            "proof": "ORIGINAL_RECEIPT",
            "proofNote": "Customer provided original receipt"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns")
            .header("Idempotency-Key", "test-key-12345")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("return-789"))
            .andExpect(jsonPath("$.state").value("REQUESTED"));
        
        verify(returnService).create(eq("test-key-12345"), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"}) // Missing POS_RETURN_CREATE
    void createReturnReturns403WithoutCreateAuthority() throws Exception {
        String requestBody = """
            {
            "orderId": "order-456",
            "sessionId": "session-123",
            "items": [{"productId": 1, "quantity": 2}],
            "reason": "Defective product",
            "proof": "ORIGINAL_RECEIPT",
            "proofNote": "Customer provided original receipt"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns")
            .header("Idempotency-Key", "test-key-12345")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).create(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_RETURN_CREATE"}) // Missing POS_VIEW and POS_RETURN_VIEW
    void createReturnReturns403WithoutBaseAuthorities() throws Exception {
        String requestBody = """
            {
            "orderId": "order-456",
            "sessionId": "session-123",
            "items": [{"productId": 1, "quantity": 2}],
            "reason": "Defective product",
            "proof": "ORIGINAL_RECEIPT",
            "proofNote": "Customer provided original receipt"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns")
            .header("Idempotency-Key", "test-key-12345")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).create(any(), any());
    }
    
    @Test
    void createReturnReturns401WithoutAuthentication() throws Exception {
        String requestBody = """
            {
            "orderId": "order-456",
            "sessionId": "session-123",
            "items": [{"productId": 1, "quantity": 2}],
            "reason": "Defective product",
            "proof": "ORIGINAL_RECEIPT",
            "proofNote": "Customer provided original receipt"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns")
            .header("Idempotency-Key", "test-key-12345")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isUnauthorized());
        
        verify(returnService, never()).create(any(), any());
    }
    
    // ==================== REVIEW ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_APPROVE"})
    void reviewReturnSucceedsWithRequiredAuthorities() throws Exception {
        when(returnService.review(any(), any())).thenReturn(
            new ReturnService.View(
                "return-789",
                "order-456",
                "APPROVED",
                "cashier@vendor.com",
                "Defective product",
                "ORIGINAL_RECEIPT",
                "Customer provided original receipt",
                Instant.now().minusSeconds(3600),
                "supervisor@vendor.com",
                "Return approved after verification",
                null,
                "NOT_INITIATED",
                List.of(new ReturnService.Line(1L, "Product A", 2, null, null, null))
            )
        );
        
        String requestBody = """
            {
            "decision": "APPROVED",
            "reason": "Return approved after verification"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("return-789"))
            .andExpect(jsonPath("$.state").value("APPROVED"))
            .andExpect(jsonPath("$.reviewedBy").value("supervisor@vendor.com"));
        
        verify(returnService).review(eq("return-789"), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"}) // Missing POS_RETURN_APPROVE
    void reviewReturnReturns403WithoutApproveAuthority() throws Exception {
        String requestBody = """
            {
            "decision": "APPROVED",
            "reason": "Return approved after verification"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).review(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_CREATE"}) // Wrong authority
    void reviewReturnReturns403WithCreateAuthorityOnly() throws Exception {
        String requestBody = """
            {
            "decision": "APPROVED",
            "reason": "Return approved after verification"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).review(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_RETURN_APPROVE"}) // Missing POS_VIEW and POS_RETURN_VIEW
    void reviewReturnReturns403WithoutBaseAuthorities() throws Exception {
        String requestBody = """
            {
            "decision": "APPROVED",
            "reason": "Return approved after verification"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).review(any(), any());
    }
    
    @Test
    void reviewReturnReturns401WithoutAuthentication() throws Exception {
        String requestBody = """
            {
            "decision": "APPROVED",
            "reason": "Return approved after verification"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isUnauthorized());
        
        verify(returnService, never()).review(any(), any());
    }
    
    // ==================== INSPECT ENDPOINT TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_INSPECT"})
    void inspectReturnSucceedsWithRequiredAuthorities() throws Exception {
        when(returnService.inspect(any(), any())).thenReturn(
            new ReturnService.View(
                "return-789",
                "order-456",
                "INSPECTED",
                "cashier@vendor.com",
                "Defective product",
                "ORIGINAL_RECEIPT",
                "Customer provided original receipt",
                Instant.now().minusSeconds(7200),
                "supervisor@vendor.com",
                "Return approved after verification",
                "inspector@vendor.com",
                "NOT_INITIATED",
                List.of(new ReturnService.Line(1L, "Product A", 2, "DAMAGED", "DISPOSE", "Item has physical damage"))
            )
        );
        
        String requestBody = """
            {
            "items": [{
                "productId": 1,
                "condition": "DAMAGED",
                "disposition": "DISPOSE",
                "note": "Item has physical damage"
            }]
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/inspect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("return-789"))
            .andExpect(jsonPath("$.state").value("INSPECTED"))
            .andExpect(jsonPath("$.inspectedBy").value("inspector@vendor.com"));
        
        verify(returnService).inspect(eq("return-789"), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW"}) // Missing POS_RETURN_INSPECT
    void inspectReturnReturns403WithoutInspectAuthority() throws Exception {
        String requestBody = """
            {
            "items": [{
                "productId": 1,
                "condition": "DAMAGED",
                "disposition": "DISPOSE",
                "note": "Item has physical damage"
            }]
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/inspect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).inspect(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_APPROVE"}) // Wrong authority
    void inspectReturnReturns403WithApproveAuthorityOnly() throws Exception {
        String requestBody = """
            {
            "items": [{
                "productId": 1,
                "condition": "DAMAGED",
                "disposition": "DISPOSE",
                "note": "Item has physical damage"
            }]
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/inspect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).inspect(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_RETURN_INSPECT"}) // Missing POS_VIEW and POS_RETURN_VIEW
    void inspectReturnReturns403WithoutBaseAuthorities() throws Exception {
        String requestBody = """
            {
            "items": [{
                "productId": 1,
                "condition": "DAMAGED",
                "disposition": "DISPOSE",
                "note": "Item has physical damage"
            }]
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/inspect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).inspect(any(), any());
    }
    
    @Test
    void inspectReturnReturns401WithoutAuthentication() throws Exception {
        String requestBody = """
            {
            "items": [{
                "productId": 1,
                "condition": "DAMAGED",
                "disposition": "DISPOSE",
                "note": "Item has physical damage"
            }]
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/inspect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isUnauthorized());
        
        verify(returnService, never()).inspect(any(), any());
    }
    
    // ==================== AUTHORITY SEPARATION TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_CREATE"})
    void createAuthorityDoesNotGrantReviewAccess() throws Exception {
        when(returnService.list(any())).thenReturn(List.of());
        
        // READ allowed
        mvc.perform(get("/api/v1/vendor/pos/returns?state=REQUESTED"))
            .andExpect(status().isOk());
        
        // REVIEW blocked
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\": \"APPROVED\", \"reason\": \"Test\"}"))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).review(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_APPROVE"})
    void approveAuthorityDoesNotGrantInspectAccess() throws Exception {
        // REVIEW allowed (mocked)
        String reviewBody = "{\"decision\": \"APPROVED\", \"reason\": \"Test\"}";
        when(returnService.review(any(), any())).thenReturn(
            new ReturnService.View("return-789", "order-456", "APPROVED", "cashier@vendor.com",
                "Test", "ORIGINAL_RECEIPT", "Note", Instant.now(), "supervisor@vendor.com",
                "Test", null, "NOT_INITIATED", List.of())
        );
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(reviewBody))
            .andExpect(status().isOk());
        
        // INSPECT blocked
        String inspectBody = """
            {
            "items": [{"productId": 1, "condition": "DAMAGED", "disposition": "DISPOSE", "note": "Test"}]
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/inspect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(inspectBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).inspect(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_INSPECT"})
    void inspectAuthorityDoesNotGrantCreateAccess() throws Exception {
        // CREATE blocked
        String createBody = """
            {
            "orderId": "order-456",
            "sessionId": "session-123",
            "items": [{"productId": 1, "quantity": 2}],
            "reason": "Defective product",
            "proof": "ORIGINAL_RECEIPT",
            "proofNote": "Test"
        }""";
        
        mvc.perform(post("/api/v1/vendor/pos/returns")
            .header("Idempotency-Key", "test-key-12345")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
            .andExpect(status().isForbidden());
        
        verify(returnService, never()).create(any(), any());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TESTS ====================
    
    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        // List
        mvc.perform(get("/api/v1/vendor/pos/returns?state=REQUESTED"))
            .andExpect(status().isUnauthorized());
        
        // Get
        mvc.perform(get("/api/v1/vendor/pos/returns/return-123"))
            .andExpect(status().isUnauthorized());
        
        // Eligibility
        mvc.perform(get("/api/v1/vendor/pos/returns/sale/order-456"))
            .andExpect(status().isUnauthorized());
        
        // Create
        mvc.perform(post("/api/v1/vendor/pos/returns")
            .header("Idempotency-Key", "test-key-12345")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isUnauthorized());
        
        // Review
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isUnauthorized());
        
        // Inspect
        mvc.perform(post("/api/v1/vendor/pos/returns/return-789/inspect")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
