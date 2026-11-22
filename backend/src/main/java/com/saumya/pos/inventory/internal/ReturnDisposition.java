package com.saumya.pos.inventory.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="inventory_return_dispositions") @org.hibernate.annotations.Immutable
public class ReturnDisposition {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenant;
  @Column(name="return_id",length=36) private String returnId;
  @Column(name="product_id") private long product;
  public Key(){} public Key(long tenant,String id,long product){this.tenant=tenant;returnId=id;this.product=product;}
  @Override public boolean equals(Object o){return o instanceof Key k&&tenant==k.tenant&&product==k.product&&Objects.equals(returnId,k.returnId);}
  @Override public int hashCode(){return Objects.hash(tenant,returnId,product);}
 }
 @EmbeddedId private Key id;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,length=16) private String disposition;
 @Column(name="received_condition",nullable=false,length=16) private String condition;
 @Column(nullable=false,length=100) private String actor;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 protected ReturnDisposition(){}
 public ReturnDisposition(long tenant,long branch,String id,long product,int quantity,String disposition,String condition,String actor,Instant time){this.id=new Key(tenant,id,product);this.branch=branch;this.quantity=quantity;this.disposition=disposition;this.condition=condition;this.actor=actor;created=time;}
}
