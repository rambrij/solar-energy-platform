package com.saumya.pos.marketplace;
import com.saumya.pos.catalog.VendorDirectoryService;
import org.springframework.web.bind.annotation.*;
@RestController
public class VendorDirectoryController {
 private final VendorDirectoryService service;
 public VendorDirectoryController(VendorDirectoryService service){this.service=service;}
 @GetMapping("/api/marketplace/directory")
 Object search(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return service.search(q,page,size);}
}
