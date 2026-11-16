package com.saumya.pos.subscription.internal;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity @Table(name="subscription_plan_versions") @IdClass(SubscriptionPlanVersion.Key.class)
public class SubscriptionPlanVersion {
    @Id @Column(name="plan_id") private Long planId;
    @Id @Column(nullable=false) private Integer version;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal price;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="billing_interval",nullable=false,length=12) private String billingInterval;
    @Column(name="max_users",nullable=false) private int maxUsers;
    @Column(name="max_storage_mb",nullable=false) private long maxStorageMb;
    @Column(name="max_branches",nullable=false) private int maxBranches;
    @Column(name="max_warehouses",nullable=false) private int maxWarehouses;
    @Column(nullable=false,columnDefinition="json") private String entitlements;
    @Column(name="grace_days",nullable=false) private int graceDays;
    @Column(name="tax_metadata",nullable=false,columnDefinition="json") private String taxMetadata;
    @Column(name="invoice_description",nullable=false,length=500) private String invoiceDescription;
    @Column(name="created_by",nullable=false,length=100) private String createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected SubscriptionPlanVersion(){}
    public SubscriptionPlanVersion(long planId,int version,BigDecimal price,String currency,String interval,int maxUsers,long storage,int branches,int warehouses,String entitlements,int graceDays,String tax,String description,String actor,Instant now){
        this.planId=planId;this.version=version;this.price=price;this.currency=currency;this.billingInterval=interval;this.maxUsers=maxUsers;this.maxStorageMb=storage;this.maxBranches=branches;this.maxWarehouses=warehouses;this.entitlements=entitlements;this.graceDays=graceDays;this.taxMetadata=tax;this.invoiceDescription=description;this.createdBy=actor;this.createdAt=now;
    }
    public long planId(){return planId;}public int version(){return version;}public BigDecimal price(){return price;}public String currency(){return currency;}public String billingInterval(){return billingInterval;}public int maxUsers(){return maxUsers;}public long maxStorageMb(){return maxStorageMb;}public int maxBranches(){return maxBranches;}public int maxWarehouses(){return maxWarehouses;}public String entitlements(){return entitlements;}public int graceDays(){return graceDays;}public String taxMetadata(){return taxMetadata;}public String invoiceDescription(){return invoiceDescription;}public String createdBy(){return createdBy;}public Instant createdAt(){return createdAt;}
    public static class Key implements Serializable {
        public Long planId;public Integer version;
        public Key(){}
        public boolean equals(Object o){return o instanceof Key k&&Objects.equals(planId,k.planId)&&Objects.equals(version,k.version);}
        public int hashCode(){return Objects.hash(planId,version);}
    }
}
