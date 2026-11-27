package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.ProductImageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductImageController {
    private final ProductImageService service;
    public ProductImageController(ProductImageService service){this.service=service;}
    public record DeleteRequest(@NotBlank @Size(max=250) String reason){}

    @GetMapping("/api/v1/vendor/products/{productId}/images")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<ProductImageService.ImageView> listVendor(@PathVariable long productId){
        return service.listVendor(productId);
    }

    @PostMapping(value="/api/v1/vendor/products/{productId}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ProductImageService.ImageView upload(@PathVariable long productId,@RequestPart("file") MultipartFile file,
            @RequestParam(required=false) String altText,@RequestParam String reason) throws java.io.IOException {
        return service.upload(productId,file.getBytes(),file.getContentType(),altText,reason);
    }

    @GetMapping("/api/v1/vendor/products/{productId}/images/{imageId}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ResponseEntity<byte[]> readVendor(@PathVariable long productId,@PathVariable String imageId){
        return response(service.vendorImage(productId,imageId));
    }

    @DeleteMapping("/api/v1/vendor/products/{productId}/images/{imageId}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ResponseEntity<Void> deactivate(@PathVariable long productId,@PathVariable String imageId,
            @Valid @RequestBody DeleteRequest request){
        service.deactivate(productId,imageId,request.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/marketplace/vendors/{slug}/products/{productId}/images")
    @PreAuthorize("permitAll()")
    public List<ProductImageService.ImageView> listPublic(@PathVariable String slug,@PathVariable long productId){
        return service.publicList(slug,productId);
    }

    @GetMapping("/api/v1/marketplace/vendors/{slug}/products/{productId}/images/{imageId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> readPublic(@PathVariable String slug,@PathVariable long productId,@PathVariable String imageId){
        return response(service.publicImage(slug,productId,imageId));
    }

    private static ResponseEntity<byte[]> response(ProductImageService.ImageContent image){
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
            .header(HttpHeaders.X_CONTENT_TYPE_OPTIONS,"nosniff")
            .body(image.content());
    }
}
