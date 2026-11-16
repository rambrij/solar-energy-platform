package com.saumya.pos.marketplace;

import com.saumya.pos.inventory.StockReceivingService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.retail.PosService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/v1/vendor/inventory/receipts")
public class StockReceivingController {
 private final StockReceivingService receiving;private final PosService retail;private final CatalogService catalog;
 public StockReceivingController(StockReceivingService receiving,PosService retail,CatalogService catalog){this.receiving=receiving;this.retail=retail;this.catalog=catalog;}
 
 @PostMapping
 @Transactional
 @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
 public Object receive(
         @RequestHeader("Idempotency-Key") String key,
         @Valid @RequestBody StockReceivingService.Input input
 ) {
  var scope = retail.scope();
  retail.lockOutlet(scope);
  catalog.product(scope.tenant(), input.productId());
  return receiving.receive(scope.tenant(), scope.branch(), scope.cashier(), key, input);
 }
 
 @GetMapping
 @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
 public Object list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
  var scope = retail.scope();
  return receiving.list(scope.tenant(), scope.branch(), page, size);
 }
}
