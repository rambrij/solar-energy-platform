package com.saumya.pos.marketplace;
import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class MarketplaceController {
    private final CartService service; private final CatalogService catalogue; private final CustomerAccess customers;
    public MarketplaceController(CartService service,CatalogService catalogue,CustomerAccess customers){this.service=service;this.catalogue=catalogue;this.customers=customers;}
    @GetMapping("/api/marketplace/vendors") Object vendors(){return catalogue.vendors();}
    @GetMapping("/api/marketplace/vendors/{slug}/products") Object products(@PathVariable String slug){return catalogue.products(slug);}
    @GetMapping("/api/customer/csrf") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object csrf(CsrfToken token){return Map.of("token",token.getToken());}
    @GetMapping("/api/customer/cart") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object cart(Principal p){return service.cart(customers.resolve(p.getName()));}
    @PostMapping("/api/customer/cart/items") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object put(Principal p,@Valid @RequestBody CartService.Item item){return service.put(customers.resolve(p.getName()),item);}
    @DeleteMapping("/api/customer/cart/items/{productId}") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object remove(Principal p,@PathVariable long productId){return service.remove(customers.resolve(p.getName()),productId);}
    @PostMapping("/api/customer/cart/switch") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object switchVendor(Principal p,@Valid @RequestBody CartService.Switch request){return service.switchVendor(customers.resolve(p.getName()),request);}
    @PostMapping("/api/customer/cart/quote") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object quote(Principal p){return service.quote(customers.resolve(p.getName()));}
    @GetMapping("/api/customer/cart/history") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object history(Principal p){return service.history(customers.resolve(p.getName()));}
    @PostMapping("/api/customer/cart/restore") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object restore(Principal p,@Valid @RequestBody CartService.Restore request){return service.restore(customers.resolve(p.getName()),request);}
}
