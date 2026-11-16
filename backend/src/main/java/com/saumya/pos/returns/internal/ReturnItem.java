package com.saumya.pos.returns.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="pos_return_items")
public class ReturnItem {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenant;
  @Column(name="return_id",length=36) private String returnId;
  @Column(name="product_id") private long product;
  public Key(){} public Key(long tenant,String id,long product){this.tenant=tenant;returnId=id;this.product=product;}
  @Override public boolean equals(Object o){return o instanceof Key k&&tenant==k.tenant&&product==k.product&&Objects.equals(returnId,k.returnId);}
  @Override public int hashCode(){return Objects.hash(tenant,returnId,product);}
 }
 @EmbeddedId private Key id;
 @Column(name="order_id",nullable=false,length=36) private String orderId;
 @Column(nullable=false,length=160) private String name;
 @Column(nullable=false) private int quantity;
 @Column(name="received_condition",length=16) private String condition;
 @Column(length=16) private String disposition;
 @Column(name="inspection_note",length=250) private String note;
 protected ReturnItem(){}
 public ReturnItem(long tenant,String id,String order,long product,String name,int quantity){this.id=new Key(tenant,id,product);orderId=order;this.name=name;this.quantity=quantity;}
 public long product(){return id.product;} public int quantity(){return quantity;} public String name(){return name;} public String condition(){return condition;} public String disposition(){return disposition;} public String note(){return note;}
 public void inspect(String condition,String disposition,String note){this.condition=condition;this.disposition=disposition;this.note=note;}
}
