package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="catalog_promotions")
public class Promotion {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenantId;
  @Column(name="product_id") private long productId;
  public Key(){} public Key(long tenant,long product){tenantId=tenant;productId=product;}
  @Override public boolean equals(Object other){return other instanceof Key k&&tenantId==k.tenantId&&productId==k.productId;}
  @Override public int hashCode(){return Objects.hash(tenantId,productId);}
 }
 @EmbeddedId private Key id;
 @Column(nullable=false,precision=5,scale=2) private BigDecimal percent;
 @Column(name="updated_by",nullable=false,length=100) private String actor;
 @Column(nullable=false,length=250) private String reason;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="updated_at",nullable=false) private Instant updated;
 protected Promotion(){}
 public Promotion(long tenant,long product,BigDecimal percent,String actor,String reason,Instant time){id=new Key(tenant,product);change(percent,actor,reason,time);}
 public BigDecimal percent(){return percent;}
 public void change(BigDecimal percent,String actor,String reason,Instant time){this.percent=percent;this.actor=actor;this.reason=reason;updated=time;}
}
