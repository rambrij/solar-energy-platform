package com.saumya.pos.catalog;

import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class ProductImageService {
    private static final int MAX_BYTES=5*1024*1024;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ObjectStoragePort storage;
    private final CatalogChangeEventRepository events;
    private final AuthorizationContext authorization;
    private final CatalogService catalog;
    private final Clock clock;

    public ProductImageService(ProductRepository products,ProductImageRepository images,ObjectStoragePort storage,
            CatalogChangeEventRepository events,AuthorizationContext authorization,CatalogService catalog,Clock clock) {
        this.products=products;this.images=images;this.storage=storage;this.events=events;
        this.authorization=authorization;this.catalog=catalog;this.clock=clock;
    }

    public record ImageView(String id,String contentType,long byteSize,String sha256,String altText,
            java.time.Instant createdAt,String url) {}
    public record ImageContent(String contentType,byte[] content) {}

    private AuthorizationContext.Context actor(String permission) {
        var context=authorization.current();context.requireVendorMembership();context.requirePermission(permission);return context;
    }
    private Product requireProduct(long tenant,long productId) {
        return products.findByTenantIdAndId(tenant,productId).orElseThrow(
            ()->new ResponseStatusException(NOT_FOUND,"Product unavailable"));
    }
    private static String detectedType(byte[] b) {
        if(b.length>=8&&(b[0]&255)==0x89&&b[1]==0x50&&b[2]==0x4e&&b[3]==0x47&&
            b[4]==0x0d&&b[5]==0x0a&&b[6]==0x1a&&b[7]==0x0a)return "image/png";
        if(b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff)return "image/jpeg";
        if(b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&
            b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "image/webp";
        throw new ResponseStatusException(BAD_REQUEST,"Only valid PNG, JPEG and WebP images are accepted");
    }
    private static String hash(byte[] content) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static String normalizeAlt(String value,String fallback) {
        String alt=value==null||value.isBlank()?fallback:value.strip();
        if(alt.length()>180)throw new ResponseStatusException(BAD_REQUEST,"Image alt text must be at most 180 characters");
        return alt;
    }
    private static ImageView view(ProductImage image,String url) {
        return new ImageView(image.id(),image.contentType(),image.byteSize(),image.sha256(),image.altText(),image.createdAt(),url);
    }
    private String vendorUrl(long productId,String imageId) {
        return "/api/v1/vendor/products/"+productId+"/images/"+imageId;
    }
    private String publicUrl(String slug,long productId,String imageId) {
        return "/api/v1/marketplace/vendors/"+slug+"/products/"+productId+"/images/"+imageId;
    }

    @Transactional(readOnly=true)
    public List<ImageView> listVendor(long productId) {
        var context=actor("PRODUCT_VIEW");requireProduct(context.tenantId(),productId);
        return images.findByTenantIdAndProductIdAndDeletedAtIsNullOrderByCreatedAtDesc(context.tenantId(),productId)
            .stream().map(image->view(image,vendorUrl(productId,image.id()))).toList();
    }

    @Transactional
    public ImageView upload(long productId,byte[] content,String declaredType,String altText,String reason) {
        var context=actor("PRODUCT_UPDATE");
        Product product=requireProduct(context.tenantId(),productId);
        if(content==null||content.length==0||content.length>MAX_BYTES)
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE,"Product images must be between 1 byte and 5 MB");
        String mediaType=detectedType(content);
        if(declaredType!=null&&!declaredType.isBlank()&&!declaredType.equals("application/octet-stream")&&!declaredType.equals(mediaType))
            throw new ResponseStatusException(BAD_REQUEST,"Image content type does not match the uploaded file");
        String auditReason=reason==null?"":reason.strip();
        if(auditReason.isBlank()||auditReason.length()>250)
            throw new ResponseStatusException(BAD_REQUEST,"A reason of at most 250 characters is required");
        String id=UUID.randomUUID().toString();
        String key="tenants/"+context.tenantId()+"/products/"+productId+"/images/"+id;
        String digest=hash(content);
        var now=clock.instant();
        storage.put(key,content,mediaType);
        try {
            var image=images.saveAndFlush(new ProductImage(id,context.tenantId(),productId,key,mediaType,
                content.length,digest,normalizeAlt(altText,product.name()),context.username(),now));
            events.saveAndFlush(new CatalogChangeEvent(context.tenantId(),context.username(),"PRODUCT_IMAGE_UPLOAD",
                productId,auditReason,"",id+":"+mediaType+":"+content.length+":"+digest,now));
            return view(image,vendorUrl(productId,id));
        } catch(RuntimeException failure) {
            try{storage.delete(key);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    @Transactional
    public void deactivate(long productId,String imageId,String reason) {
        var context=actor("PRODUCT_UPDATE");requireProduct(context.tenantId(),productId);
        if(reason==null||reason.isBlank()||reason.strip().length()>250)
            throw new ResponseStatusException(BAD_REQUEST,"A reason of at most 250 characters is required");
        var image=images.locked(context.tenantId(),productId,imageId).orElseThrow(
            ()->new ResponseStatusException(NOT_FOUND,"Product image unavailable"));
        image.deactivate(clock.instant());
        events.saveAndFlush(new CatalogChangeEvent(context.tenantId(),context.username(),"PRODUCT_IMAGE_DEACTIVATE",
            productId,reason.strip(),imageId,imageId+":DEACTIVATED",clock.instant()));
    }

    @Transactional(readOnly=true)
    public ImageContent vendorImage(long productId,String imageId) {
        var context=actor("PRODUCT_VIEW");requireProduct(context.tenantId(),productId);
        var image=images.findByTenantIdAndProductIdAndIdAndDeletedAtIsNull(context.tenantId(),productId,imageId)
            .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Product image unavailable"));
        return new ImageContent(image.contentType(),storage.get(image.objectKey()));
    }

    @Transactional(readOnly=true)
    public List<ImageView> publicList(String slug,long productId) {
        var vendor=catalog.activeVendor(slug);catalog.published(vendor.id(),productId);
        return images.findByTenantIdAndProductIdAndDeletedAtIsNullOrderByCreatedAtDesc(vendor.id(),productId)
            .stream().map(image->view(image,publicUrl(slug,productId,image.id()))).toList();
    }

    @Transactional(readOnly=true)
    public ImageContent publicImage(String slug,long productId,String imageId) {
        var vendor=catalog.activeVendor(slug);catalog.published(vendor.id(),productId);
        var image=images.findByTenantIdAndProductIdAndIdAndDeletedAtIsNull(vendor.id(),productId,imageId)
            .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Product image unavailable"));
        return new ImageContent(image.contentType(),storage.get(image.objectKey()));
    }
}
