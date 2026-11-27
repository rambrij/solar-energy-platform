package com.saumya.pos.catalog.internal;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="product_image_metadata",indexes={
    @Index(name="ix_product_image_tenant_product_created",columnList="tenant_id,product_id,created_at,id")
})
public class ProductImage {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenantId;
    @Column(name="product_id",nullable=false) private long productId;
    @Column(name="object_key",nullable=false,length=512,unique=true) private String objectKey;
    @Column(name="content_type",nullable=false,length=40) private String contentType;
    @Column(name="byte_size",nullable=false) private long byteSize;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name="sha256",nullable=false,length=64) private String sha256;
    @Column(name="alt_text",nullable=false,length=180) private String altText;
    @Column(name="created_by",nullable=false,length=100) private String createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="deleted_at") private Instant deletedAt;

    protected ProductImage() {}
    public ProductImage(String id,long tenantId,long productId,String objectKey,String contentType,long byteSize,
            String sha256,String altText,String createdBy,Instant createdAt) {
        this.id=id;this.tenantId=tenantId;this.productId=productId;this.objectKey=objectKey;
        this.contentType=contentType;this.byteSize=byteSize;this.sha256=sha256;this.altText=altText;
        this.createdBy=createdBy;this.createdAt=createdAt;
    }
    public void deactivate(Instant at){if(deletedAt==null)deletedAt=at;}
    public String id(){return id;} public long tenantId(){return tenantId;} public long productId(){return productId;}
    public String objectKey(){return objectKey;} public String contentType(){return contentType;}
    public long byteSize(){return byteSize;} public String sha256(){return sha256;} public String altText(){return altText;}
    public String createdBy(){return createdBy;} public Instant createdAt(){return createdAt;} public Instant deletedAt(){return deletedAt;}
}
