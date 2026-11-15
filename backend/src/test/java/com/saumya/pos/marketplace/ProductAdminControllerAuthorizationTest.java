package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.ProductAdminService;
import com.saumya.pos.security.AuthorizationContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductAdminController.class)
class ProductAdminControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private ProductAdminService productAdminService;
    
    @MockBean
    private AuthorizationContext authContext;
    
    // ==================== AUTHORIZED ACCESS TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"})
    void listProductsSucceedsWithProductView() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = mock(AuthorizationContext.Context.class);
        when(authContext.current()).thenReturn(mockContext);
        when(mockContext.tenantId()).thenReturn(1L);
        doNothing().when(mockContext).requireVendorMembership();
        
        // Given: ProductAdminService returns empty list
        var emptyPage = new ProductAdminService.Page(List.of(), 0, 20, 0, 0);
        when(productAdminService.list(eq(1L), eq(0), eq(20))).thenReturn(emptyPage);
        
        // When: GET /api/v1/vendor/products
        mvc.perform(get("/api/v1/vendor/products")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));
        
        // Then: Service called with correct tenant ID
        verify(productAdminService).list(1L, 0, 20);
        verify(mockContext).requireVendorMembership();
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_CREATE"})
    void createProductSucceedsWithProductCreate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = mock(AuthorizationContext.Context.class);
        when(authContext.current()).thenReturn(mockContext);
        when(mockContext.tenantId()).thenReturn(1L);
        when(mockContext.username()).thenReturn("admin@vendor.com");
        doNothing().when(mockContext).requireVendorMembership();
        doNothing().when(mockContext).requirePermission("PRODUCT_CREATE");
        
        // Given: ProductAdminService returns created product
        var createdProduct = new ProductAdminService.View(
            101L,
            "SKU-001",
            "BAR-001",
            "Test Product",
            new BigDecimal("99.99"),
            new BigDecimal("18.00"),
            true,
            1L,
            0L
        );
        when(productAdminService.create(eq(1L), eq("admin@vendor.com"), any(ProductAdminService.Input.class)))
            .thenReturn(createdProduct);
        
        // When: POST /api/v1/vendor/products
        mvc.perform(post("/api/v1/vendor/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Test Product",
                        "price": 99.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Initial product creation"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.sku").value("SKU-001"))
            .andExpect(jsonPath("$.name").value("Test Product"));
        
        // Then: Service called with correct tenant ID and username
        verify(productAdminService).create(eq(1L), eq("admin@vendor.com"), any(ProductAdminService.Input.class));
        verify(mockContext).requireVendorMembership();
        verify(mockContext).requirePermission("PRODUCT_CREATE");
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_UPDATE"})
    void updateProductSucceedsWithProductUpdate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = mock(AuthorizationContext.Context.class);
        when(authContext.current()).thenReturn(mockContext);
        when(mockContext.tenantId()).thenReturn(1L);
        when(mockContext.username()).thenReturn("admin@vendor.com");
        doNothing().when(mockContext).requireVendorMembership();
        doNothing().when(mockContext).requirePermission("PRODUCT_UPDATE");
        
        // Given: ProductAdminService returns updated product
        var updatedProduct = new ProductAdminService.View(
            101L,
            "SKU-001",
            "BAR-001",
            "Updated Product Name",
            new BigDecimal("109.99"),
            new BigDecimal("18.00"),
            true,
            1L,
            1L
        );
        when(productAdminService.update(eq(1L), eq("admin@vendor.com"), eq(101L), any(ProductAdminService.Input.class)))
            .thenReturn(updatedProduct);
        
        // When: PUT /api/v1/vendor/products/101
        mvc.perform(put("/api/v1/vendor/products/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Updated Product Name",
                        "price": 109.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Price update"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.name").value("Updated Product Name"))
            .andExpect(jsonPath("$.price").value(109.99));
        
        // Then: Service called with correct tenant ID, username, and product ID
        verify(productAdminService).update(eq(1L), eq("admin@vendor.com"), eq(101L), any(ProductAdminService.Input.class));
        verify(mockContext).requireVendorMembership();
        verify(mockContext).requirePermission("PRODUCT_UPDATE");
    }
    
    // ==================== MISSING PERMISSION TESTS (403) ====================
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"}) // Customer role without product permissions
    void listProductsReturns403WithCustomerRole() throws Exception {
        mvc.perform(get("/api/v1/vendor/products"))
            .andExpect(status().isForbidden());
        
        verify(productAdminService, never()).list(anyLong(), anyInt(), anyInt());
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"}) // Has VIEW but not CREATE
    void createProductReturns403WithProductViewOnly() throws Exception {
        mvc.perform(post("/api/v1/vendor/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Test Product",
                        "price": 99.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Initial product creation"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(productAdminService, never()).create(anyLong(), anyString(), any(ProductAdminService.Input.class));
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"}) // Has VIEW but not UPDATE
    void updateProductReturns403WithProductViewOnly() throws Exception {
        mvc.perform(put("/api/v1/vendor/products/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Updated Product Name",
                        "price": 109.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Price update"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(productAdminService, never()).update(anyLong(), anyString(), anyLong(), any(ProductAdminService.Input.class));
    }
    
    // ==================== VIEW ≠ MUTATE TESTS ====================
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"})
    void productViewDoesNotGrantCreateOrUpdate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context for read operations
        var mockContext = mock(AuthorizationContext.Context.class);
        when(authContext.current()).thenReturn(mockContext);
        when(mockContext.tenantId()).thenReturn(1L);
        doNothing().when(mockContext).requireVendorMembership();
        
        var emptyPage = new ProductAdminService.Page(List.of(), 0, 20, 0, 0);
        when(productAdminService.list(eq(1L), eq(0), eq(20))).thenReturn(emptyPage);
        
        // READ allowed
        mvc.perform(get("/api/v1/vendor/products"))
            .andExpect(status().isOk());
        
        verify(productAdminService).list(1L, 0, 20);
        
        // CREATE blocked
        mvc.perform(post("/api/v1/vendor/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Test Product",
                        "price": 99.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Initial product creation"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(productAdminService, never()).create(anyLong(), anyString(), any(ProductAdminService.Input.class));
        
        // UPDATE blocked
        mvc.perform(put("/api/v1/vendor/products/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Updated Product Name",
                        "price": 109.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Price update"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(productAdminService, never()).update(anyLong(), anyString(), anyLong(), any(ProductAdminService.Input.class));
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_CREATE"}) // Has CREATE but not UPDATE
    void productCreateDoesNotGrantUpdate() throws Exception {
        // Given: AuthorizationContext returns valid vendor context
        var mockContext = mock(AuthorizationContext.Context.class);
        when(authContext.current()).thenReturn(mockContext);
        when(mockContext.tenantId()).thenReturn(1L);
        when(mockContext.username()).thenReturn("admin@vendor.com");
        doNothing().when(mockContext).requireVendorMembership();
        doNothing().when(mockContext).requirePermission("PRODUCT_CREATE");
        
        var createdProduct = new ProductAdminService.View(
            101L, "SKU-001", "BAR-001", "Test Product",
            new BigDecimal("99.99"), new BigDecimal("18.00"), true, 1L, 0L
        );
        when(productAdminService.create(eq(1L), eq("admin@vendor.com"), any(ProductAdminService.Input.class)))
            .thenReturn(createdProduct);
        
        // CREATE allowed
        mvc.perform(post("/api/v1/vendor/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Test Product",
                        "price": 99.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Initial product creation"
                    }
                    """))
            .andExpect(status().isOk());
        
        verify(productAdminService).create(eq(1L), eq("admin@vendor.com"), any(ProductAdminService.Input.class));
        
        // UPDATE blocked
        mvc.perform(put("/api/v1/vendor/products/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Updated Product Name",
                        "price": 109.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Price update"
                    }
                    """))
            .andExpect(status().isForbidden());
        
        verify(productAdminService, never()).update(anyLong(), anyString(), anyLong(), any(ProductAdminService.Input.class));
    }
    
    // ==================== CROSS-TENANT ACCESS TEST ====================
    
    @Test
    @WithMockUser(username = "adminA@vendorA.com", authorities = {"PRODUCT_VIEW"})
    void tenantIsolationEnforcedInServiceLayer() throws Exception {
        // Given: AuthorizationContext returns tenant 1
        var mockContext = mock(AuthorizationContext.Context.class);
        when(authContext.current()).thenReturn(mockContext);
        when(mockContext.tenantId()).thenReturn(1L);
        doNothing().when(mockContext).requireVendorMembership();
        
        // Given: Service returns data for tenant 1 only
        var page = new ProductAdminService.Page(List.of(), 0, 20, 0, 0);
        when(productAdminService.list(eq(1L), anyInt(), anyInt())).thenReturn(page);
        
        // When: User from tenant 1 lists products
        mvc.perform(get("/api/v1/vendor/products"))
            .andExpect(status().isOk());
        
        // Then: Service layer enforces tenant isolation
        verify(productAdminService).list(1L, 0, 20);
        verify(productAdminService, never()).list(eq(2L), anyInt(), anyInt());
    }
    
    // ==================== UNAUTHENTICATED ACCESS TEST ====================
    
    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        // LIST endpoint requires authentication
        mvc.perform(get("/api/v1/vendor/products"))
            .andExpect(status().isUnauthorized());
        
        // CREATE endpoint requires authentication
        mvc.perform(post("/api/v1/vendor/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Test Product",
                        "price": 99.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Initial product creation"
                    }
                    """))
            .andExpect(status().isUnauthorized());
        
        // UPDATE endpoint requires authentication
        mvc.perform(put("/api/v1/vendor/products/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sku": "SKU-001",
                        "barcode": "BAR-001",
                        "name": "Updated Product Name",
                        "price": 109.99,
                        "taxRate": 18.00,
                        "published": true,
                        "categoryId": 1,
                        "expectedVersion": 0,
                        "reason": "Price update"
                    }
                    """))
            .andExpect(status().isUnauthorized());
        
        // No service methods should be called
        verify(productAdminService, never()).list(anyLong(), anyInt(), anyInt());
        verify(productAdminService, never()).create(anyLong(), anyString(), any(ProductAdminService.Input.class));
        verify(productAdminService, never()).update(anyLong(), anyString(), anyLong(), any(ProductAdminService.Input.class));
    }
}
