package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="catalog_stock")
public class CatalogStock {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenant;
  @Column(name="branch_id") private long branch;
  @Column(name="product_id") private long product;
  protected Key(){}public Key(long tenant,long branch,long product){this.tenant=tenant;this.branch=branch;this.product=product;}
  public boolean equals(Object other){return other instanceof Key k&&tenant==k.tenant&&branch==k.branch&&product==k.product;}public int hashCode(){return Objects.hash(tenant,branch,product);}
 }
 @EmbeddedId private Key id;
 @Column(nullable=false) private int available;
 protected CatalogStock(){}public CatalogStock(Key id,int available){this.id=id;this.available=available;}
 public void availability(int available){this.available=available;}public int available(){return available;}
}
