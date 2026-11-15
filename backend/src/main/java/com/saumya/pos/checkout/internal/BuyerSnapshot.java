package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
import java.time.Instant;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="order_buyer_snapshots")
public class BuyerSnapshot {
 @Embeddable public static class Key implements Serializable{
  @Column(name="tenant_id") private long tenant;
  @Column(name="order_id",length=36) private String order;
  protected Key(){}public Key(long tenant,String order){this.tenant=tenant;this.order=order;}
  public boolean equals(Object other){return other instanceof Key k&&tenant==k.tenant&&Objects.equals(order,k.order);}public int hashCode(){return Objects.hash(tenant,order);}
 }
 @EmbeddedId private Key id;
 @Column(length=160) private String name;
 @Column(length=160) private String contact;
 @Column(name="tax_id",length=80) private String taxId;
 @Column(length=1000) private String address;
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP) @Column(name="captured_at",nullable=false) private Instant captured;
 protected BuyerSnapshot(){}
 public BuyerSnapshot(long tenant,String order,String name,String contact,String taxId,String address,Instant captured){id=new Key(tenant,order);this.name=name;this.contact=contact;this.taxId=taxId;this.address=address;this.captured=captured;}
 public String name(){return name;}public String contact(){return contact;}public String taxId(){return taxId;}public String address(){return address;}
}
