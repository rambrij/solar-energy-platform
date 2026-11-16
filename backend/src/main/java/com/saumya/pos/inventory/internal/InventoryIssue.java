package com.saumya.pos.inventory.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="inventory_ledger")
public class InventoryIssue {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenant;
  @Column(name="order_id",length=36) private String order;
  @Column(name="product_id") private long product;
  protected Key(){}
  Key(long tenant,String order,long product){this.tenant=tenant;this.order=order;this.product=product;}
  public boolean equals(Object other){return other instanceof Key k&&tenant==k.tenant&&product==k.product&&Objects.equals(order,k.order);}
  public int hashCode(){return Objects.hash(tenant,order,product);}
 }
 @EmbeddedId private Key id;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(name="quantity_delta",nullable=false) private int delta;
 protected InventoryIssue(){}
 public InventoryIssue(long tenant,String order,long product,long branch,int quantity){id=new Key(tenant,order,product);this.branch=branch;delta=-quantity;}
}
