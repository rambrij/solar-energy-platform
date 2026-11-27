package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.ProductImageService;
import com.saumya.pos.security.AuthorizationContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.CacheControl;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
public class ProductImageController {
    private static final long MAX_IMAGE_BYTES=5L*1024L*1024L;
    private final ProductImageService service;
    private final AuthorizationContext authorization;
    public ProductImageController(ProductImageService service,AuthorizationContext authorization){
        this.service=service;this.authorization=authorization;
    }
    public record DeleteRequest(@NotBlank @Size(max=250) String reason){}

    @GetMapping("/api/v1/vendor/products/{productId}/images")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<ProductImageService.ImageView> listVendor(@PathVariable long productId){
        return service.listVendor(productId);
    }

    @PostMapping("/api/v1/vendor/products/{productId}/images")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ProductImageService.ImageView upload(@PathVariable long productId,HttpServletRequest request)
            throws IOException,ServletException {
        // Resolve the permission gate before parsing a multipart body. This ensures a
        // caller without vendor update access is denied consistently, even when the
        // request has no valid multipart content type.
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("PRODUCT_UPDATE");

        String requestType=request.getContentType();
        if(requestType==null||!requestType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data"))
            throw new ResponseStatusException(UNSUPPORTED_MEDIA_TYPE,"A multipart product image upload is required");
        Part file;
        try{file=request.getPart("file");}
        catch(ServletException|IOException invalid){
            throw new ResponseStatusException(BAD_REQUEST,"Invalid multipart product image upload",invalid);
        }
        if(file==null)throw new ResponseStatusException(BAD_REQUEST,"Product image file is required");
        if(file.getSize()>MAX_IMAGE_BYTES)
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE,"Product images must not exceed 5 MB");
        byte[] content;
        try(var input=file.getInputStream()){content=input.readNBytes((int)MAX_IMAGE_BYTES+1);}
        if(content.length>MAX_IMAGE_BYTES)
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE,"Product images must not exceed 5 MB");
        return service.upload(productId,content,file.getContentType(),request.getParameter("altText"),request.getParameter("reason"));
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
            .header("X-Content-Type-Options","nosniff")
            .body(image.content());
    }
}
