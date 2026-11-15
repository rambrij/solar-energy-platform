package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.ProductAdminService;
import com.saumya.pos.membership.MembershipService;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vendor/products")
public class ProductAdminController {
 private final ProductAdminService products;private final MembershipService memberships;
 public ProductAdminController(ProductAdminService products,MembershipService memberships){this.products=products;this.memberships=memberships;}
 @GetMapping public Object list(Principal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return products.list(memberships.require(actor.getName()).tenant(),page,size);}
 @PostMapping public Object create(Principal actor,@Valid @RequestBody ProductAdminService.Input request){return products.create(memberships.require(actor.getName()).tenant(),actor.getName(),request);}
 @PutMapping("/{id}") public Object update(Principal actor,@PathVariable long id,@Valid @RequestBody ProductAdminService.Input request){return products.update(memberships.require(actor.getName()).tenant(),actor.getName(),id,request);}
}
