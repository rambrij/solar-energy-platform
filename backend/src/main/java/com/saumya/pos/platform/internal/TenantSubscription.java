package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A tenant's subscription with the agreed terms snapshotted at creation, so later plan/price changes never alter history (SUB-003). */
@Entity @Table(name="tenant_subscriptions")
public class TenantSubscription {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenant;
    @Column(name="plan_version_id",nullable=false,length=36) private String planVersionId;
    @Column(name="plan_code",nullable=false,length=40) private String planCode;
    @Column(name="plan_version",nullable=false) private int planVersion;
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
    @Column(nullable=false,length=24) private String status;
    @Column(name="status_reason",length=500) private String statusReason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="current_period_start") private Instant periodStart;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="current_period_end") private Instant periodEnd;
    /** 1 while this is the tenant's single effective subscription, NULL once ended; UNIQUE(tenant_id,effective) enforces SUB-004. */
    @Column(name="effective") private Integer effective;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="created_by",nullable=false,length=100) private String createdBy;
    protected TenantSubscription(){}
    public TenantSubscription(String id,long tenant,PlanVersion v,Instant now,String actor){
        this.id=id;this.tenant=tenant;planVersionId=v.id();planCode=v.planCode();planVersion=v.version();interval=v.interval();price=v.price();currency=v.currency();taxRate=v.taxRate();taxInclusive=v.taxInclusive();graceDays=v.graceDays();maxUsers=v.maxUsers();maxBranches=v.maxBranches();maxProducts=v.maxProducts();features=v.features();
        status="PENDING";effective=1;createdAt=now.truncatedTo(ChronoUnit.MICROS);createdBy=actor;
    }
    public String id(){return id;} public long tenant(){return tenant;} public String planVersionId(){return planVersionId;} public String planCode(){return planCode;} public int planVersion(){return planVersion;} public String interval(){return interval;}
    public BigDecimal price(){return price;} public String currency(){return currency;} public BigDecimal taxRate(){return taxRate;} public boolean taxInclusive(){return taxInclusive;} public int graceDays(){return graceDays;}
    public Integer maxUsers(){return maxUsers;} public Integer maxBranches(){return maxBranches;} public Integer maxProducts(){return maxProducts;} public String features(){return features;}
    public String status(){return status;} public String statusReason(){return statusReason;} public Instant periodStart(){return periodStart;} public Instant periodEnd(){return periodEnd;} public boolean isEffective(){return effective!=null;} public Instant createdAt(){return createdAt;} public String createdBy(){return createdBy;}
    public void status(String status,String reason,boolean terminal){this.status=status;statusReason=reason;if(terminal)effective=null;}
    public void period(Instant start,Instant end){periodStart=start.truncatedTo(ChronoUnit.MICROS);periodEnd=end.truncatedTo(ChronoUnit.MICROS);}
}
