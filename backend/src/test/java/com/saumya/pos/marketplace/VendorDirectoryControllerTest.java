package com.saumya.pos.marketplace;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;

import com.saumya.pos.catalog.VendorDirectoryService;
import com.saumya.pos.catalog.VendorDirectoryService.VendorCard;
import com.saumya.pos.catalog.VendorDirectoryService.Directory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for VendorDirectoryController focusing on public access configuration.
 * Verifies that @PreAuthorize("permitAll()") allows unauthenticated marketplace browsing
 * while also testing authenticated access scenarios.
 */
@WebMvcTest(VendorDirectoryController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class VendorDirectoryControllerTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private VendorDirectoryService service;
    
    // ==================== PUBLIC ACCESS TESTS (permitAll) ====================
    
    @Test
    void unauthenticatedAccessIsAllowed() throws Exception {
        // Given: Service returns empty directory
        Directory emptyResult = new Directory(
            "",      // query
            0,       // page
            12,      // size
            0L,      // totalElements
            0,       // totalPages
            Collections.emptyList() // items
        );
        when(service.search("", 0, 12)).thenReturn(emptyResult);
        
        // When: Unauthenticated request is made
        // Then: Should return 200 OK (not 401 Unauthorized)
        mvc.perform(get("/api/v1/marketplace/directory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value(""))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(12))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.items").isArray());
        
        verify(service).search("", 0, 12);
    }
    
    @Test
    void unauthenticatedAccessWithSearchQuery() throws Exception {
        // Given: Service returns search results
        List<VendorCard> vendors = List.of(
            new VendorCard(1L, "solar-panels-inc", "Solar Panels Inc"),
            new VendorCard(2L, "green-energy-co", "Green Energy Co")
        );
        Directory searchResult = new Directory(
            "solar",  // query
            0,        // page
            12,       // size
            2L,       // totalElements
            1,        // totalPages
            vendors
        );
        when(service.search("solar", 0, 12)).thenReturn(searchResult);
        
        // When: Unauthenticated search request is made
        // Then: Should return 200 OK with results
        mvc.perform(get("/api/v1/marketplace/directory")
                .param("q", "solar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value("solar"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].id").value(1))
            .andExpect(jsonPath("$.items[0].slug").value("solar-panels-inc"))
            .andExpect(jsonPath("$.items[1].id").value(2));
        
        verify(service).search("solar", 0, 12);
    }
    
    // ==================== AUTHENTICATED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(username = "customer@example.com", authorities = {"CUSTOMER"})
    void authenticatedCustomerCanAccess() throws Exception {
        // Given: Service returns empty directory
        Directory emptyResult = new Directory(
            "", 0, 12, 0L, 0, Collections.emptyList()
        );
        when(service.search("", 0, 12)).thenReturn(emptyResult);
        
        // When: Authenticated customer makes request
        // Then: Should return 200 OK (permitAll allows everyone)
        mvc.perform(get("/api/v1/marketplace/directory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value(""));
        
        verify(service).search("", 0, 12);
    }
    
    @Test
    @WithMockUser(username = "admin@vendor.com", authorities = {"VENDOR_ADMIN"})
    void authenticatedVendorAdminCanAccess() throws Exception {
        // Given: Service returns empty directory
        Directory emptyResult = new Directory(
            "", 0, 12, 0L, 0, Collections.emptyList()
        );
        when(service.search("", 0, 12)).thenReturn(emptyResult);
        
        // When: Authenticated vendor admin makes request
        // Then: Should return 200 OK (permitAll allows everyone)
        mvc.perform(get("/api/v1/marketplace/directory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value(""));
        
        verify(service).search("", 0, 12);
    }
    
    // ==================== PARAMETER VERIFICATION TESTS ====================
    
    @Test
    void serviceIsCalledWithDefaultParameters() throws Exception {
        // Given: Service returns empty directory
        Directory emptyResult = new Directory(
            "", 0, 12, 0L, 0, Collections.emptyList()
        );
        when(service.search("", 0, 12)).thenReturn(emptyResult);
        
        // When: Request is made without parameters
        mvc.perform(get("/api/v1/marketplace/directory"))
            .andExpect(status().isOk());
        
        // Then: Service should be called with defaults: q="", page=0, size=12
        verify(service).search(eq(""), eq(0), eq(12));
    }
    
    @Test
    void serviceIsCalledWithCustomQueryParameter() throws Exception {
        // Given: Service returns search results
        Directory searchResult = new Directory(
            "renewable", 0, 12, 5L, 1, Collections.emptyList()
        );
        when(service.search("renewable", 0, 12)).thenReturn(searchResult);
        
        // When: Request is made with custom query
        mvc.perform(get("/api/v1/marketplace/directory")
                .param("q", "renewable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value("renewable"));
        
        // Then: Service should be called with q="renewable"
        verify(service).search(eq("renewable"), eq(0), eq(12));
    }
    
    @Test
    void serviceIsCalledWithCustomPaginationParameters() throws Exception {
        // Given: Service returns paginated results
        Directory paginatedResult = new Directory(
            "energy", 2, 24, 100L, 5, Collections.emptyList()
        );
        when(service.search("energy", 2, 24)).thenReturn(paginatedResult);
        
        // When: Request is made with custom page and size
        mvc.perform(get("/api/v1/marketplace/directory")
                .param("q", "energy")
                .param("page", "2")
                .param("size", "24"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(24));
        
        // Then: Service should be called with custom parameters
        verify(service).search(eq("energy"), eq(2), eq(24));
    }
    
    @Test
    void serviceIsCalledWithAllCustomParameters() throws Exception {
        // Given: Service returns search results
        Directory searchResult = new Directory(
            "battery storage", 5, 6, 50L, 9, Collections.emptyList()
        );
        when(service.search("battery storage", 5, 6)).thenReturn(searchResult);
        
        // When: Request is made with all custom parameters
        mvc.perform(get("/api/v1/marketplace/directory")
                .param("q", "battery storage")
                .param("page", "5")
                .param("size", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value("battery storage"))
            .andExpect(jsonPath("$.page").value(5))
            .andExpect(jsonPath("$.size").value(6));
        
        // Then: Service should be called with exact parameters
        verify(service).search(eq("battery storage"), eq(5), eq(6));
    }
    
    // ==================== EDGE CASES ====================
    
    @Test
    void emptyQueryStringDefaultsToEmptyString() throws Exception {
        // Given: Service returns empty directory
        Directory emptyResult = new Directory(
            "", 0, 12, 0L, 0, Collections.emptyList()
        );
        when(service.search("", 0, 12)).thenReturn(emptyResult);
        
        // When: Request is made with empty query string
        mvc.perform(get("/api/v1/marketplace/directory")
                .param("q", ""))
            .andExpect(status().isOk());
        
        // Then: Service should be called with empty string
        verify(service).search(eq(""), eq(0), eq(12));
    }
    
    @Test
    void zeroPageReturnsFirstPage() throws Exception {
        // Given: Service returns first page
        Directory firstPage = new Directory(
            "", 0, 12, 25L, 3, Collections.emptyList()
        );
        when(service.search("", 0, 12)).thenReturn(firstPage);
        
        // When: Request is made with page=0
        mvc.perform(get("/api/v1/marketplace/directory")
                .param("page", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0));
        
        // Then: Service should be called with page=0
        verify(service).search(eq(""), eq(0), eq(12));
    }
}
