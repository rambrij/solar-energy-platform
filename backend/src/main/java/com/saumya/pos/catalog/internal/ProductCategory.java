package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="product_categories")
public class ProductCategory {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenantId;
  @Column(name="product_id") private long productId;
  public Key(){}
  @Override public boolean equals(Object other){return other instanceof Key k&&tenantId==k.tenantId&&productId==k.productId;}
  @Override public int hashCode(){return Objects.hash(tenantId,productId);}
 }
 @EmbeddedId private Key id;
 @Column(name="category_id",nullable=false) private long categoryId;
 protected ProductCategory(){}
}
