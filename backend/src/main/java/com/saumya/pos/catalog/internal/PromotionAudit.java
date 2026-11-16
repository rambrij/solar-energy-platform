package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="catalog_promotion_audit")
public class PromotionAudit {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="product_id",nullable=false) private long product;
 @Column(name="previous_percent",nullable=false,precision=5,scale=2) private BigDecimal previous;
 @Column(name="new_percent",nullable=false,precision=5,scale=2) private BigDecimal next;
 @Column(nullable=false,length=100) private String actor;
 @Column(nullable=false,length=250) private String reason;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 protected PromotionAudit(){}
 public PromotionAudit(long tenant,long product,BigDecimal previous,BigDecimal next,String actor,String reason,Instant time){id=UUID.randomUUID().toString();this.tenant=tenant;this.product=product;this.previous=previous;this.next=next;this.actor=actor;this.reason=reason;created=time;}
}
