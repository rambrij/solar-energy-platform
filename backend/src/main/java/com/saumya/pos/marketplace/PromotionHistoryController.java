package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.PromotionHistoryService;
import com.saumya.pos.retail.PosService;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class PromotionHistoryController {
 private final PromotionHistoryService history;private final PosService retail;
 public PromotionHistoryController(PromotionHistoryService history,PosService retail){this.history=history;this.retail=retail;}
 @GetMapping("/api/vendor/pos/promotions/{product}/history")
 @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('CATALOG_PROMOTION_MANAGE') and hasAuthority('POS_DISCOUNT_APPROVE')")
 Object history(Principal p,@PathVariable long product,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){var scope=retail.scope(p.getName());return history.history(scope.tenant(),product,page,size);}
}
