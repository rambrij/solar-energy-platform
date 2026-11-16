package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One immutable version of a plan's price and entitlements. Changing terms creates a new version; subscriptions keep their snapshot. */
@Entity @Table(name="subscription_plan_versions") @Immutable
public class PlanVersion {
    @Id @Column(length=36) private String id;
    @Column(name="plan_code",nullable=false,length=40) private String planCode;
    @Column(nullable=false) private int version;
    @Column(name="billing_interval",nullable=false,length=10) private String interval;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal price;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="tax_rate",nullable=false,precision=5,scale=2) private BigDecimal taxRate;
    @Column(name="tax_inclusive",nullable=false) private boolean taxInclusive;
    @Column(name="grace_days",nullable=false) private int graceDays;
    @Column(name="max_users") private Integer maxUsers;
    @Column(name="max_branches") private Integer maxBranches;
    @Column(name="max_products") private Integer maxProducts;
    @Column(nullable=false,length=1000) private String features;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false,length=100) private String createdBy;
    protected PlanVersion(){}
    public PlanVersion(String id,String planCode,int version,String interval,BigDecimal price,String currency,BigDecimal taxRate,boolean taxInclusive,int graceDays,Integer maxUsers,Integer maxBranches,Integer maxProducts,String features,Instant now,String actor){
        this.id=id;this.planCode=planCode;this.version=version;this.interval=interval;this.price=price;this.currency=currency;this.taxRate=taxRate;this.taxInclusive=taxInclusive;this.graceDays=graceDays;this.maxUsers=maxUsers;this.maxBranches=maxBranches;this.maxProducts=maxProducts;this.features=features;createdAt=now.truncatedTo(ChronoUnit.MICROS);createdBy=actor;
    }
    public String id(){return id;} public String planCode(){return planCode;} public int version(){return version;} public String interval(){return interval;} public BigDecimal price(){return price;} public String currency(){return currency;}
    public BigDecimal taxRate(){return taxRate;} public boolean taxInclusive(){return taxInclusive;} public int graceDays(){return graceDays;} public Integer maxUsers(){return maxUsers;} public Integer maxBranches(){return maxBranches;} public Integer maxProducts(){return maxProducts;}
    public String features(){return features;} public Instant createdAt(){return createdAt;} public String createdBy(){return createdBy;}
}
