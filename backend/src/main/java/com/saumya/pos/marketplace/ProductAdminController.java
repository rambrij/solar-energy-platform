package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.ProductAdminService;
import com.saumya.pos.security.AuthorizationContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor/products")
public class ProductAdminController {
    private final ProductAdminService products;
    private final AuthorizationContext authContext;
    
    public ProductAdminController(
        ProductAdminService products,
        AuthorizationContext authContext
    ) {
        this.products = products;
        this.authContext = authContext;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public Object list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(name = "q", required = false) String query,
        @RequestParam(required = false) Boolean published,
        @RequestParam(required = false) Long categoryId
    ) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        return categoryId == null && published == null && (query == null || query.isBlank()) ? products.list(ctx.tenantId(), page, size)
            : products.search(ctx.tenantId(), page, size, query, published, categoryId);
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public Object create(@Valid @RequestBody ProductAdminService.Input request) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        ctx.requirePermission("PRODUCT_CREATE");
        return products.create(ctx.tenantId(), ctx.username(), request);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public Object update(
        @PathVariable long id,
        @Valid @RequestBody ProductAdminService.Input request
    ) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        ctx.requirePermission("PRODUCT_UPDATE");
        return products.update(ctx.tenantId(), ctx.username(), id, request);
    }
}
