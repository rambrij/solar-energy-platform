package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.PromotionService;
import com.saumya.pos.retail.PosService;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class PromotionController {
 private final PromotionService promotions; private final PosService retail;
 public PromotionController(PromotionService promotions,PosService retail){this.promotions=promotions;this.retail=retail;}
 @PutMapping("/api/vendor/catalog/products/{id}/promotion")
 @PreAuthorize("hasAuthority('CATALOG_PROMOTION_MANAGE') and hasAuthority('POS_DISCOUNT_APPROVE')")
 Object approve(Principal principal,@PathVariable long id,@Valid @RequestBody PromotionService.Change request){var scope=retail.scope(principal.getName());return promotions.approve(scope.tenant(),scope.cashier(),id,request);}
}
