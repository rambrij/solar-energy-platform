package com.saumya.pos.marketplace.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "site_surveys")
public class SiteSurvey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private long tenantId;
    @Column(name = "enquiry_id", nullable = false, updatable = false) private long enquiryId;
    @Column(name = "site_address", nullable = false, length = 500) private String siteAddress;
    @Column(name = "scheduled_at", nullable = false) private Instant scheduledAt;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "roof_type", length = 40) private String roofType;
    @Column(name = "monthly_consumption_kwh", precision = 12, scale = 2) private BigDecimal monthlyConsumptionKwh;
    @Column(name = "proposed_capacity_kw", precision = 10, scale = 2) private BigDecimal proposedCapacityKw;
    @Column(length = 2000) private String notes;
    @Column(length = 4000) private String findings;
    @Column(name = "assigned_to", nullable = false, length = 100) private String assignedTo;
    @Column(name = "created_by", nullable = false, length = 100, updatable = false) private String createdBy;
    @Column(name = "updated_by", nullable = false, length = 100) private String updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected SiteSurvey() {}

    public SiteSurvey(long tenantId, long enquiryId, String siteAddress, Instant scheduledAt,
            String status, String roofType, BigDecimal monthlyConsumptionKwh,
            BigDecimal proposedCapacityKw, String notes, String assignedTo,
            String createdBy, Instant now) {
        this.tenantId=tenantId;
        this.enquiryId=enquiryId;
        this.siteAddress=siteAddress;
        this.scheduledAt=scheduledAt;
        this.status=status;
        this.roofType=roofType;
        this.monthlyConsumptionKwh=monthlyConsumptionKwh;
        this.proposedCapacityKw=proposedCapacityKw;
        this.notes=notes;
        this.assignedTo=assignedTo;
        this.createdBy=createdBy;
        this.updatedBy=createdBy;
        this.createdAt=now;
        this.updatedAt=now;
    }

    public void update(String status, Instant scheduledAt, String roofType,
            BigDecimal monthlyConsumptionKwh, BigDecimal proposedCapacityKw,
            String findings, String actor, Instant now) {
        this.status=status;
        if(scheduledAt!=null)this.scheduledAt=scheduledAt;
        if(roofType!=null)this.roofType=roofType;
        if(monthlyConsumptionKwh!=null)this.monthlyConsumptionKwh=monthlyConsumptionKwh;
        if(proposedCapacityKw!=null)this.proposedCapacityKw=proposedCapacityKw;
        if(findings!=null)this.findings=findings;
        this.updatedBy=actor;
        this.updatedAt=now;
    }

    public Long id(){return id;}
    public long tenantId(){return tenantId;}
    public long enquiryId(){return enquiryId;}
    public String siteAddress(){return siteAddress;}
    public Instant scheduledAt(){return scheduledAt;}
    public String status(){return status;}
    public String roofType(){return roofType;}
    public BigDecimal monthlyConsumptionKwh(){return monthlyConsumptionKwh;}
    public BigDecimal proposedCapacityKw(){return proposedCapacityKw;}
    public String notes(){return notes;}
    public String findings(){return findings;}
    public String assignedTo(){return assignedTo;}
    public String createdBy(){return createdBy;}
    public String updatedBy(){return updatedBy;}
    public Instant createdAt(){return createdAt;}
    public Instant updatedAt(){return updatedAt;}
    public long version(){return version;}
}
