package com.saumya.pos.subscription.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="tenant_subscriptions")
public class TenantSubscription {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="plan_id",nullable=false) private Long planId;
    @Column(name="plan_version",nullable=false) private Integer planVersion;
    @Column(nullable=false,length=30) private String status;
    @Column(name="billing_period_start",nullable=false) private LocalDate periodStart;
    @Column(name="billing_period_end",nullable=false) private LocalDate periodEnd;
    @Column(name="grace_until") private Instant graceUntil;
    @Column(name="terms_snapshot",nullable=false,columnDefinition="json") private String termsSnapshot;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected TenantSubscription(){}
    public TenantSubscription(long tenantId,long planId,int planVersion,LocalDate start,LocalDate end,String termsSnapshot,Instant now){
        this.tenantId=tenantId;this.planId=planId;this.planVersion=planVersion;this.periodStart=start;this.periodEnd=end;this.termsSnapshot=termsSnapshot;this.status="PENDING";this.createdAt=now;this.updatedAt=now;
    }
    public long id(){return id;}public long tenantId(){return tenantId;}public long planId(){return planId;}public int planVersion(){return planVersion;}public String status(){return status;}public LocalDate periodStart(){return periodStart;}public LocalDate periodEnd(){return periodEnd;}public Instant graceUntil(){return graceUntil;}public String termsSnapshot(){return termsSnapshot;}public Instant createdAt(){return createdAt;}public Instant updatedAt(){return updatedAt;}public long version(){return version;}
    public void transition(String next,Instant now,int graceDays){
        this.status=next;this.updatedAt=now;this.graceUntil="GRACE_PERIOD".equals(next)?now.plusSeconds(graceDays*86400L):null;
    }

    public boolean expireGraceAt(Instant now) {
        if (!"GRACE_PERIOD".equals(status) || graceUntil == null || graceUntil.isAfter(now)) return false;
        transition("SUSPENDED", now, 0);
        return true;
    }
}
