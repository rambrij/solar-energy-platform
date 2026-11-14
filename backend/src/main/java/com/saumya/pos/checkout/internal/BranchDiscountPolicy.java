package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
import java.math.BigDecimal;
/** Read-only view of a branch's cashier discount ceiling (percent). Zero disables cashier-initiated discounts. */
@Entity @Table(name="branches") @org.hibernate.annotations.Immutable
public class BranchDiscountPolicy {
    @Id private Long id;
    @Column(name="tenant_id",nullable=false) private long tenant;
    @Column(name="manual_discount_limit_percent",nullable=false,precision=5,scale=2) private BigDecimal limit;
    protected BranchDiscountPolicy(){}
    public BigDecimal limit(){return limit;}
}
