package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.CatalogSearchService;
import org.springframework.web.bind.annotation.*;
@RestController
public class CatalogSearchController {
 private final CatalogSearchService service;
 public CatalogSearchController(CatalogSearchService service){this.service=service;}
 @GetMapping("/api/marketplace/vendors/{slug}/catalogue")
 Object search(@PathVariable String slug,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String category,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="false") boolean inStock,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){return service.search(slug,q,category,sort,inStock,page,size);}
}
