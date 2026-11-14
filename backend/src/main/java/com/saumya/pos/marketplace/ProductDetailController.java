package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.ProductDetailService;
import org.springframework.web.bind.annotation.*;
@RestController
public class ProductDetailController {
    private final ProductDetailService details;
    public ProductDetailController(ProductDetailService details){this.details=details;}
    @GetMapping("/api/marketplace/vendors/{slug}/products/{id}")
    Object detail(@PathVariable String slug,@PathVariable long id){return details.detail(slug,id);}
}
