package com.saumya.pos;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class MarketplaceController {
    private final MarketplaceService service;
    public MarketplaceController(MarketplaceService service){this.service=service;}
    @GetMapping("/api/marketplace/vendors") Object vendors(){return service.vendors();}
    @GetMapping("/api/marketplace/vendors/{slug}/products") Object products(@PathVariable String slug){return service.products(slug);}
    @GetMapping("/api/customer/csrf") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object csrf(CsrfToken token){return Map.of("token",token.getToken());}
    @GetMapping("/api/customer/cart") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object cart(Principal p){return service.cart(service.customer(p.getName()));}
    @PostMapping("/api/customer/cart/items") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object put(Principal p,@Valid @RequestBody MarketplaceService.Item item){return service.put(service.customer(p.getName()),item);}
    @DeleteMapping("/api/customer/cart/items/{productId}") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object remove(Principal p,@PathVariable long productId){return service.remove(service.customer(p.getName()),productId);}
    @PostMapping("/api/customer/cart/switch") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object switchVendor(Principal p,@Valid @RequestBody MarketplaceService.Switch request){return service.switchVendor(service.customer(p.getName()),request);}
    @PostMapping("/api/customer/cart/quote") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object quote(Principal p){return service.quote(service.customer(p.getName()));}
}
