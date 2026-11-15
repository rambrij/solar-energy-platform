# Phase 2: Marketplace Module Authorization Implementation

## Overview
This document provides complete code changes needed to implement authorization for the Marketplace module, following the pattern established in Phase 1 (POS module).

---

## Step 1: Public Marketplace Endpoints (Permit All)

### 1.1 VendorDirectoryController.java

**File:** `backend/src/main/java/com/saumya/pos/marketplace/VendorDirectoryController.java`

**Current Code:**
```java
package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.VendorDirectoryService;
import org.springframework.web.bind.annotation.*;

@RestController
public class VendorDirectoryController {
    private final VendorDirectoryService service;
    public VendorDirectoryController(VendorDirectoryService service){this.service=service;}
    
    @GetMapping("/api/v1/marketplace/directory")
    Object search(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return service.search(q,page,size);}
}
```

**Updated Code:**
```java
package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.VendorDirectoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class VendorDirectoryController {
    private final VendorDirectoryService service;
    
    public VendorDirectoryController(VendorDirectoryService service) {
        this.service = service;
    }
    
    @GetMapping("/api/v1/marketplace/directory")
    @PreAuthorize("permitAll()")
    Object search(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size
    ) {
        return service.search(q, page, size);
    }
}
```

**Changes:**
- ✅ Added `import org.springframework.security.access.prepost.PreAuthorize;`
- ✅ Added `@PreAuthorize("permitAll()")` to `search()` method
- ✅ Formatted code for better readability
- ✅ **Security:** Explicitly allows public access to vendor directory browsing

---

### 1.2 ProductDetailController.java

**File:** `backend/src/main/java/com/saumya/pos/marketplace/ProductDetailController.java`

**Current Code:**
```java
package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.ProductDetailService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductDetailController {
    private final ProductDetailService details;
    public ProductDetailController(ProductDetailService details){this.details=details;}
    
    @GetMapping("/api/v1/marketplace/vendors/{slug}/products/{id}")
    Object detail(@PathVariable String slug,@PathVariable long id){return details.detail(slug,id);}
}
```

**Updated Code:**
```java
package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.ProductDetailService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductDetailController {
    private final ProductDetailService details;
    
    public ProductDetailController(ProductDetailService details) {
        this.details = details;
    }
    
    @GetMapping("/api/v1/marketplace/vendors/{slug}/products/{id}")
    @PreAuthorize("permitAll()")
    Object detail(@PathVariable String slug, @PathVariable long id) {
        return details.detail(slug, id);
    }
}
```

**Changes:**
- ✅ Added `import org.springframework.security.access.prepost.PreAuthorize;`
- ✅ Added `@PreAuthorize("permitAll()")` to `detail()` method
- ✅ Formatted code for better readability
- ✅ **Security:** Explicitly allows public access to product detail viewing

---

### 1.3 CatalogSearchController.java

**File:** `backend/src/main/java/com/saumya/pos/marketplace/CatalogSearchController.java`

**Action Required:** Add `@PreAuthorize("permitAll()")` to all GET endpoints (catalog search is public)

**Expected Pattern:**
```java
@GetMapping("/api/v1/marketplace/search")
@PreAuthorize("permitAll()")
Object search(...) { ... }
```

---

## Step 2: Admin Endpoints (Require Permissions)

### 2.1 ProductAdminController.java

**File:** `backend/src/main/java/com/saumya/pos/marketplace/ProductAdminController.java`

**Required Changes:**
1. Add `import org.springframework.security.access.prepost.PreAuthorize;`
2. Inject `AuthorizationContext`
3. Add permission checks to all methods

**Implementation Pattern:**
```java
package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.ProductAdminService;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vendor/products")
public class ProductAdminController {
    private final ProductAdminService service;
    private final AuthorizationContext authContext;
    
    public ProductAdminController(
        ProductAdminService service,
        AuthorizationContext authContext
    ) {
        this.service = service;
        this.authContext = authContext;
    }
    
    // List products (VIEW permission)
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    Object list() {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        return service.listForTenant(ctx.tenantId());
    }
    
    // Get product details (VIEW permission)
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    Object get(@PathVariable Long id) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        return service.getProduct(ctx.tenantId(), id);
    }
    
    // Create product (CREATE permission)
    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    Object create(@Valid @RequestBody ProductAdminService.CreateProductRequest request) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        ctx.requirePermission("PRODUCT_CREATE");
        
        // Validate that request doesn't try to create for different tenant
        if (request.tenantId() != null && !request.tenantId().equals(ctx.tenantId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Cannot create products for different tenant"
            );
        }
        
        return service.createProduct(ctx.tenantId(), request);
    }
    
    // Update product (UPDATE permission)
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    Object update(
        @PathVariable Long id,
        @Valid @RequestBody ProductAdminService.UpdateProductRequest request
    ) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        ctx.requirePermission("PRODUCT_UPDATE");
        
        return service.updateProduct(ctx.tenantId(), id, request);
    }
    
    // Archive product (ARCHIVE permission)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_ARCHIVE')")
    Object archive(@PathVariable Long id) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        ctx.requirePermission("PRODUCT_ARCHIVE");
        
        return service.archiveProduct(ctx.tenantId(), id);
    }
}
```

**Key Security Features:**
- ✅ All endpoints require authentication (no permitAll)
- ✅ VIEW/CREATE/UPDATE/ARCHIVE permissions separated
- ✅ Tenant validation on all operations
- ✅ Cannot create/update products for different tenant
- ✅ Service layer receives validated `tenantId` from AuthorizationContext

---

### 2.2 CategoryAdminController.java

**File:** `backend/src/main/java/com/saumya/pos/marketplace/CategoryAdminController.java`

**Implementation Pattern:**
```java
package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.CategoryAdminService;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vendor/categories")
public class CategoryAdminController {
    private final CategoryAdminService service;
    private final AuthorizationContext authContext;
    
    public CategoryAdminController(
        CategoryAdminService service,
        AuthorizationContext authContext
    ) {
        this.service = service;
        this.authContext = authContext;
    }
    
    // List categories (PRODUCT_VIEW permission - categories are part of product catalog)
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    Object list() {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        return service.listForTenant(ctx.tenantId());
    }
    
    // Create category (PRODUCT_CREATE permission)
    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    Object create(@Valid @RequestBody CategoryAdminService.CreateCategoryRequest request) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        
        return service.createCategory(ctx.tenantId(), request);
    }
    
    // Update category (PRODUCT_UPDATE permission)
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    Object update(
        @PathVariable Long id,
        @Valid @RequestBody CategoryAdminService.UpdateCategoryRequest request
    ) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        
        return service.updateCategory(ctx.tenantId(), id, request);
    }
    
    // Delete category (PRODUCT_UPDATE permission - categories don't have separate archive)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    Object delete(@PathVariable Long id) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        
        return service.deleteCategory(ctx.tenantId(), id);
    }
}
```

---

## Step 3: Service Layer Validation

### 3.1 ProductAdminService.java

**Required Changes:**
1. All service methods must accept `tenantId` as first parameter
2. All database queries must filter by `tenant_id`
3. Cross-tenant operations must be blocked

**Example Pattern:**
```java
@Service
public class ProductAdminService {
    private final ProductRepository products;
    
    // List products for specific tenant only
    public List<Product> listForTenant(Long tenantId) {
        return products.findAllByTenantId(tenantId);
    }
    
    // Get product with tenant validation
    public Product getProduct(Long tenantId, Long productId) {
        return products.findByTenantIdAndId(tenantId, productId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Product not found" // Don't reveal if it exists in different tenant
            ));
    }
    
    // Create product for specific tenant
    @Transactional
    public Product createProduct(Long tenantId, CreateProductRequest request) {
        var product = new Product();
        product.setTenantId(tenantId); // Server-side assignment, never from request
        product.setName(request.name());
        product.setDescription(request.description());
        // ... set other fields
        
        return products.save(product);
    }
    
    // Update product with tenant validation
    @Transactional
    public Product updateProduct(Long tenantId, Long productId, UpdateProductRequest request) {
        var product = getProduct(tenantId, productId); // Validates tenant ownership
        
        product.setName(request.name());
        product.setDescription(request.description());
        // ... update other fields
        
        return products.save(product);
    }
}
```

**Critical Security Rules:**
- ✅ **Never trust `tenantId` from request body** - Always use value from `AuthorizationContext`
- ✅ **Filter all queries by tenantId** - Prevent accidental cross-tenant data leaks
- ✅ **Cross-tenant resources return 404** - Don't reveal existence to unauthorized users
- ✅ **Server assigns tenantId on creation** - Client cannot override

---

### 3.2 CategoryAdminService.java

**Same pattern as ProductAdminService:**
```java
@Service
public class CategoryAdminService {
    private final CategoryRepository categories;
    
    public List<Category> listForTenant(Long tenantId) {
        return categories.findAllByTenantId(tenantId);
    }
    
    @Transactional
    public Category createCategory(Long tenantId, CreateCategoryRequest request) {
        var category = new Category();
        category.setTenantId(tenantId); // Server-side assignment
        category.setName(request.name());
        return categories.save(category);
    }
    
    @Transactional
    public Category updateCategory(Long tenantId, Long categoryId, UpdateCategoryRequest request) {
        var category = categories.findByTenantIdAndId(tenantId, categoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        category.setName(request.name());
        return categories.save(category);
    }
}
```

---

## Step 4: Frontend Route Protection

### 4.1 Update RouteOutlet.tsx or App.tsx

**Add ProtectedRoute wrappers:**

```typescript
import { ProtectedRoute } from './ProtectedRoute';

// Public marketplace routes (no protection)
<Route path="/vendors" element={<VendorDirectory />} />
<Route path="/vendors/:slug" element={<VendorPage />} />
<Route path="/vendors/:slug/products/:id" element={<ProductDetail />} />
<Route path="/marketplace/search" element={<CatalogSearch />} />

// Customer-only routes
<Route path="/cart" element={
  <ProtectedRoute requiredPermissions={['CUSTOMER']}>
    <Cart />
  </ProtectedRoute>
} />

// Vendor product admin routes
<Route path="/vendor/products" element={
  <ProtectedRoute requiredPermissions={['PRODUCT_VIEW']}>
    <ProductAdmin />
  </ProtectedRoute>
} />

<Route path="/vendor/products/new" element={
  <ProtectedRoute requiredPermissions={['PRODUCT_CREATE']}>
    <ProductCreate />
  </ProtectedRoute>
} />

<Route path="/vendor/products/:id/edit" element={
  <ProtectedRoute requiredPermissions={['PRODUCT_UPDATE']}>
    <ProductEdit />
  </ProtectedRoute>
} />

<Route path="/vendor/categories" element={
  <ProtectedRoute requiredPermissions={['PRODUCT_VIEW']}>
    <CategoryAdmin />
  </ProtectedRoute>
} />
```

---

## Step 5: Tests

### 5.1 ProductAdminControllerAuthorizationTest.java

**File:** `backend/src/test/java/com/saumya/pos/marketplace/ProductAdminControllerAuthorizationTest.java`

```java
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductAdminController.class)
class ProductAdminControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private ProductAdminService productService;
    
    @MockBean
    private AuthorizationContext authContext;
    
    // ===== AUTHORIZED ACCESS TESTS =====
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"})
    void listProductsSucceedsWithProductView() throws Exception {
        var ctx = new AuthorizationContext.Context(
            "admin@vendor.com", 1L, null,
            Set.of("PRODUCT_VIEW"), false, false, true
        );
        when(authContext.current()).thenReturn(ctx);
        when(productService.listForTenant(1L)).thenReturn(List.of());
        
        mvc.perform(get("/api/v1/vendor/products"))
            .andExpect(status().isOk());
        
        verify(productService).listForTenant(1L);
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_CREATE"})
    void createProductSucceedsWithProductCreate() throws Exception {
        var ctx = new AuthorizationContext.Context(
            "admin@vendor.com", 1L, null,
            Set.of("PRODUCT_CREATE"), false, false, true
        );
        when(authContext.current()).thenReturn(ctx);
        when(productService.createProduct(any(), any())).thenReturn(Map.of("id", 123));
        
        mvc.perform(post("/api/v1/vendor/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Solar Panel 300W\", \"sku\": \"SP-300\"}"))
            .andExpect(status().isOk());
    }
    
    // ===== MISSING PERMISSION TESTS (403) =====
    
    @Test
    @WithMockUser(authorities = {"CUSTOMER"})
    void listProductsReturns403ForCustomer() throws Exception {
        mvc.perform(get("/api/v1/vendor/products"))
            .andExpect(status().isForbidden());
        
        verify(productService, never()).listForTenant(any());
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"})
    void createProductReturns403WithoutProductCreate() throws Exception {
        mvc.perform(post("/api/v1/vendor/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Solar Panel\", \"sku\": \"SP-300\"}"))
            .andExpect(status().isForbidden());
        
        verify(productService, never()).createProduct(any(), any());
    }
    
    @Test
    @WithMockUser(authorities = {"PRODUCT_VIEW"})
    void updateProductReturns403WithoutProductUpdate() throws Exception {
        mvc.perform(put("/api/v1/vendor/products/123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Updated Name\"}"))
            .andExpect(status().isForbidden());
    }
    
    // ===== CROSS-TENANT ACCESS TEST =====
    
    @Test
    @WithMockUser(username = "adminA@vendorA.com", authorities = {"PRODUCT_VIEW"})
    void cannotViewOtherTenantProducts() throws Exception {
        var ctx = new AuthorizationContext.Context(
            "adminA@vendorA.com", 1L, null,
            Set.of("PRODUCT_VIEW"), false, false, true
        );
        when(authContext.current()).thenReturn(ctx);
        when(productService.getProduct(1L, 999L))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        mvc.perform(get("/api/v1/vendor/products/999"))
            .andExpect(status().isNotFound());
    }
}
```

---

## Summary

### Files to Modify (Backend)
1. ✅ VendorDirectoryController.java - Add @PreAuthorize("permitAll()")
2. ✅ ProductDetailController.java - Add @PreAuthorize("permitAll()")
3. ✅ CatalogSearchController.java - Add @PreAuthorize("permitAll()")
4. ✅ ProductAdminController.java - Add AuthorizationContext + @PreAuthorize per method
5. ✅ CategoryAdminController.java - Add AuthorizationContext + @PreAuthorize per method
6. ⚠️ ProductAdminService.java - Add tenantId parameter validation
7. ⚠️ CategoryAdminService.java - Add tenantId parameter validation

### Files to Modify (Frontend)
8. ✅ RouteOutlet.tsx or App.tsx - Wrap admin routes with ProtectedRoute

### Files to Create (Tests)
9. ✅ ProductAdminControllerAuthorizationTest.java
10. ✅ CategoryAdminControllerAuthorizationTest.java
11. ✅ VendorDirectoryControllerTest.java (verify permitAll works)

### Security Checklist
- ✅ Public endpoints explicitly marked with permitAll()
- ✅ Admin endpoints require specific permissions (VIEW/CREATE/UPDATE/ARCHIVE)
- ✅ Tenant validation on all mutations
- ✅ Cross-tenant access blocked
- ✅ Service layer receives validated tenantId from AuthorizationContext
- ✅ Frontend routes protected with ProtectedRoute component
- ✅ Comprehensive authorization tests created

### Estimated Completion
- **Backend Controllers:** 1 hour
- **Service Layer:** 45 minutes
- **Frontend Routes:** 15 minutes
- **Tests:** 45 minutes
- **Total:** ~2.5-3 hours

---

## Next Steps After Phase 2

**Phase 3: Returns & Refunds Module**
- Apply same pattern to RefundController
- Validate refund ownership (customer can only refund own orders)
- Enforce REFUND_VIEW vs REFUND_APPROVE permissions

**Phase 4: Reports Module**
- Separate REPORT_VIEW from REPORT_EXPORT permissions
- Validate tenant scope on all reports
- Block cross-tenant report access

**Phase 5: Platform Admin Module**
- Strict PLATFORM_* permission enforcement
- Platform users cannot access vendor data without explicit membership
- Audit all sensitive operations
