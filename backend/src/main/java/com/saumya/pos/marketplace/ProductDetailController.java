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
