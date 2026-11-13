package com.saumya.pos.retail;
import com.saumya.pos.catalog.PosCatalogService;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/vendor/pos/catalogue") @PreAuthorize("hasAuthority('POS_VIEW')")
public class PosCatalogController {
 private final PosService retail;private final PosCatalogService catalogue;
 public PosCatalogController(PosService retail,PosCatalogService catalogue){this.retail=retail;this.catalogue=catalogue;}
 @GetMapping Object search(Principal p,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String category,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="false") boolean inStock,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){var scope=retail.scope(p.getName());return catalogue.search(scope.tenant(),scope.branch(),q,category,sort,inStock,page,size);}
 @GetMapping("/scan") Object scan(Principal p,@RequestParam String code){var scope=retail.scope(p.getName());return catalogue.scan(scope.tenant(),scope.branch(),code);}
}
