package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.CategoryAdminService;
import com.saumya.pos.security.AuthorizationContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor/categories")
public class CategoryAdminController {
    private final CategoryAdminService categories;
    private final AuthorizationContext authContext;
    
    public CategoryAdminController(
        CategoryAdminService categories,
        AuthorizationContext authContext
    ) {
        this.categories = categories;
        this.authContext = authContext;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public Object list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        return categories.list(ctx.tenantId(), page, size);
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public Object create(@Valid @RequestBody CategoryAdminService.Input request) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        ctx.requirePermission("PRODUCT_CREATE");
        return categories.create(ctx.tenantId(), ctx.username(), request);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public Object update(
        @PathVariable long id,
        @Valid @RequestBody CategoryAdminService.Input request
    ) {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        ctx.requirePermission("PRODUCT_UPDATE");
        return categories.update(ctx.tenantId(), ctx.username(), id, request);
    }
}
