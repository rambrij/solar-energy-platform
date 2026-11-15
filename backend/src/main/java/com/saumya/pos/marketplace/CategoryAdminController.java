package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.CategoryAdminService;
import com.saumya.pos.membership.MembershipService;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vendor/categories")
public class CategoryAdminController {
 private final CategoryAdminService categories;private final MembershipService memberships;
 public CategoryAdminController(CategoryAdminService categories,MembershipService memberships){this.categories=categories;this.memberships=memberships;}
 @GetMapping public Object list(Principal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return categories.list(memberships.require(actor.getName()).tenant(),page,size);}
 @PostMapping public Object create(Principal actor,@Valid @RequestBody CategoryAdminService.Input request){return categories.create(memberships.require(actor.getName()).tenant(),actor.getName(),request);}
 @PutMapping("/{id}") public Object update(Principal actor,@PathVariable long id,@Valid @RequestBody CategoryAdminService.Input request){return categories.update(memberships.require(actor.getName()).tenant(),actor.getName(),id,request);}
}
