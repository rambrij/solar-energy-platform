package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.CategoryAdminService;
import com.saumya.pos.security.AuthorizationContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryAdminController.class)
class CategoryAdminControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private CategoryAdminService categoryAdminService;
    
    @MockBean
    private AuthorizationContext authContext;
    
    // ==================== AUTHORIZED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"})
    void listCategoriesSucceedsWithProductView() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = new AuthorizationContext.Context("admin@vendor.com", 1L, 10L, Set.of("PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_ARCHIVE", "PRICE_UPDATE"), false, false, true);

        // Given: CategoryAdminService returns empty list
        var emptyPage = new CategoryAdminService.Page(List.of(), 0, 20, 0, 0);
        when(categoryAdminService.list(eq(1L), eq(0), eq(20))).thenReturn(emptyPage);
        
        // When: GET /api/v1/vendor/categories
        mvc.perform(get("/api/v1/vendor/categories")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));
        
        // Then: Service called with correct tenant ID
        verify(categoryAdminService).list(1L, 0, 20);

    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_CREATE"})
    void createCategorySucceedsWithProductCreate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = new AuthorizationContext.Context("admin@vendor.com", 1L, 10L, Set.of("PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_ARCHIVE", "PRICE_UPDATE"), false, false, true);


        // Given: CategoryAdminService returns created category
        var createdCategory = new CategoryAdminService.View(
            101L,
            "Electronics",
            "electronics",
            true,
            0L
        );
        when(categoryAdminService.create(eq(1L), eq("admin@vendor.com"), any(CategoryAdminService.Input.class)))
            .thenReturn(createdCategory);
        
        // When: POST /api/v1/vendor/categories
        mvc.perform(post("/api/v1/vendor/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Electronics",
                        "slug": "electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Initial category creation"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.name").value("Electronics"))
            .andExpect(jsonPath("$.slug").value("electronics"));
        
        // Then: Service called with correct tenant ID and username
        verify(categoryAdminService).create(eq(1L), eq("admin@vendor.com"), any(CategoryAdminService.Input.class));


    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_UPDATE"})
    void updateCategorySucceedsWithProductUpdate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = new AuthorizationContext.Context("admin@vendor.com", 1L, 10L, Set.of("PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_ARCHIVE", "PRICE_UPDATE"), false, false, true);


        // Given: CategoryAdminService returns updated category
        var updatedCategory = new CategoryAdminService.View(
            101L,
            "Consumer Electronics",
            "consumer-electronics",
            true,
            1L
        );
        when(categoryAdminService.update(eq(1L), eq("admin@vendor.com"), eq(101L), any(CategoryAdminService.Input.class)))
            .thenReturn(updatedCategory);
        
        // When: PUT /api/v1/vendor/categories/101
        mvc.perform(put("/api/v1/vendor/categories/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Consumer Electronics",
                        "slug": "consumer-electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Category name update"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.name").value("Consumer Electronics"))
            .andExpect(jsonPath("$.slug").value("consumer-electronics"));
        
        // Then: Service called with correct tenant ID, username, and category ID
        verify(categoryAdminService).update(eq(1L), eq("admin@vendor.com"), eq(101L), any(CategoryAdminService.Input.class));


    }
    
    // ==================== MISSING PERMISSION TESTS (403) ====================
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"}) // Customer role without product permissions
    void listCategoriesReturns403WithCustomerRole() throws Exception {
        mvc.perform(get("/api/v1/vendor/categories"))
            .andExpect(status().isForbidden());
        
        verify(categoryAdminService, never()).list(anyLong(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"}) // Has VIEW but not CREATE
    void createCategoryReturns403WithProductViewOnly() throws Exception {
        mvc.perform(post("/api/v1/vendor/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Electronics",
                        "slug": "electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Initial category creation"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(categoryAdminService, never()).create(anyLong(), anyString(), any(CategoryAdminService.Input.class));
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"}) // Has VIEW but not UPDATE
    void updateCategoryReturns403WithProductViewOnly() throws Exception {
        mvc.perform(put("/api/v1/vendor/categories/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Consumer Electronics",
                        "slug": "consumer-electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Category name update"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(categoryAdminService, never()).update(anyLong(), anyString(), anyLong(), any(CategoryAdminService.Input.class));
    }
    
    // ==================== VIEW ≠ MUTATE TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"})
    void productViewDoesNotGrantCreateOrUpdate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context for read operations
        var mockContext = new AuthorizationContext.Context("admin@vendor.com", 1L, 10L, Set.of("PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_ARCHIVE", "PRICE_UPDATE"), false, false, true);

        var emptyPage = new CategoryAdminService.Page(List.of(), 0, 20, 0, 0);
        when(categoryAdminService.list(eq(1L), eq(0), eq(20))).thenReturn(emptyPage);
        
        // READ allowed
        mvc.perform(get("/api/v1/vendor/categories"))
            .andExpect(status().isOk());
        
        verify(categoryAdminService).list(1L, 0, 20);
        
        // CREATE blocked
        mvc.perform(post("/api/v1/vendor/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Electronics",
                        "slug": "electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Initial category creation"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(categoryAdminService, never()).create(anyLong(), anyString(), any(CategoryAdminService.Input.class));
        
        // UPDATE blocked
        mvc.perform(put("/api/v1/vendor/categories/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Consumer Electronics",
                        "slug": "consumer-electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Category name update"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(categoryAdminService, never()).update(anyLong(), anyString(), anyLong(), any(CategoryAdminService.Input.class));
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_CREATE"}) // Has CREATE but not UPDATE
    void productCreateDoesNotGrantUpdate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = new AuthorizationContext.Context("admin@vendor.com", 1L, 10L, Set.of("PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_ARCHIVE", "PRICE_UPDATE"), false, false, true);


        var createdCategory = new CategoryAdminService.View(
            101L, "Electronics", "electronics", true, 0L
        );
        when(categoryAdminService.create(eq(1L), eq("admin@vendor.com"), any(CategoryAdminService.Input.class)))
            .thenReturn(createdCategory);
        
        // CREATE allowed
        mvc.perform(post("/api/v1/vendor/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Electronics",
                        "slug": "electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Initial category creation"
                    }
                    """))
            .andExpect(status().isOk());
        
        verify(categoryAdminService).create(eq(1L), eq("admin@vendor.com"), any(CategoryAdminService.Input.class));
        
        // UPDATE blocked
        mvc.perform(put("/api/v1/vendor/categories/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Consumer Electronics",
                        "slug": "consumer-electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Category name update"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(categoryAdminService, never()).update(anyLong(), anyString(), anyLong(), any(CategoryAdminService.Input.class));
    }
    
    // ==================== CROSS-TENANT ACCESS TEST ====================
    
    @Test
    @WithMockUser(username = "adminA@vendorA.com", authorities = {"PRODUCT_VIEW"})
    void tenantIsolationEnforcedInServiceLayer() throws Exception {
        // Given: AuthorizationContext returns tenant 1
        var mockContext = new AuthorizationContext.Context("admin@vendor.com", 1L, 10L, Set.of("PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_ARCHIVE", "PRICE_UPDATE"), false, false, true);

        // Given: Service returns data for tenant 1 only
        var page = new CategoryAdminService.Page(List.of(), 0, 20, 0, 0);
        when(categoryAdminService.list(eq(1L), anyInt(), anyInt())).thenReturn(page);
        
        // When: User from tenant 1 lists categories
        mvc.perform(get("/api/v1/vendor/categories"))
            .andExpect(status().isOk());
        
        // Then: Service layer enforces tenant isolation
        verify(categoryAdminService).list(1L, 0, 20);
        verify(categoryAdminService, never()).list(eq(2L), anyInt(), anyInt());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TEST ====================
    
    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        // LIST endpoint requires authentication
        mvc.perform(get("/api/v1/vendor/categories"))
            .andExpect(status().isUnauthorized());
        
        // CREATE endpoint requires authentication
        mvc.perform(post("/api/v1/vendor/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Electronics",
                        "slug": "electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Initial category creation"
                    }
                    """))
            .andExpect(status().isUnauthorized());
        
        // UPDATE endpoint requires authentication
        mvc.perform(put("/api/v1/vendor/categories/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Consumer Electronics",
                        "slug": "consumer-electronics",
                        "active": true,
                        "expectedVersion": 0,
                        "reason": "Category name update"
                    }
                    """))
            .andExpect(status().isUnauthorized());
        
        // No service methods should be called
        verify(categoryAdminService, never()).list(anyLong(), anyInt(), anyInt());
        verify(categoryAdminService, never()).create(anyLong(), anyString(), any(CategoryAdminService.Input.class));
        verify(categoryAdminService, never()).update(anyLong(), anyString(), anyLong(), any(CategoryAdminService.Input.class));
    }
}
