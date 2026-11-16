package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="catalog_discount_policy")
public class DiscountPolicy {
 @Id @Column(name="tenant_id") private Long tenant;
 @Column(name="maximum_percent",nullable=false,precision=5,scale=2) private BigDecimal maximum;
 protected DiscountPolicy(){}
 public DiscountPolicy(long tenant,BigDecimal maximum){this.tenant=tenant;this.maximum=maximum;}
 public BigDecimal maximum(){return maximum;}
}
