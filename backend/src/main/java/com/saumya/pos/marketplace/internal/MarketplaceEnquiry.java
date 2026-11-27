package com.saumya.pos.marketplace.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="marketplace_enquiries")
public class MarketplaceEnquiry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="tenant_id",nullable=false) private long tenantId;
    @Column(name="customer_id") private Long customerId;
    @Column(name="product_id") private Long productId;
    @Column(name="product_name_snapshot",length=160) private String productNameSnapshot;
    @Column(name="contact_name",nullable=false,length=120) private String contactName;
    @Column(name="contact_email",nullable=false,length=254) private String contactEmail;
    @Column(name="contact_phone",length=30) private String contactPhone;
    @Column(nullable=false,length=2000) private String message;
    @Column(name="consent_at",nullable=false) private Instant consentAt;
    // "status" remains the public-intake state; lead_status is the vendor workflow.
    @Column(name="lead_status",nullable=false,length=20) private String leadStatus;
    @Column(name="assigned_to",length=100) private String assignedTo;
    @Column(name="updated_by",length=100) private String updatedBy;
    @Column(name="updated_at") private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    @Column(name="requester_hash",nullable=false,length=64) private String requesterHash;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;

    protected MarketplaceEnquiry() {}

    public MarketplaceEnquiry(long tenantId,Long customerId,Long productId,String productNameSnapshot,
            String contactName,String contactEmail,String contactPhone,String message,
            Instant consentAt,String requesterHash,Instant createdAt) {
        this.tenantId=tenantId;this.customerId=customerId;this.productId=productId;
        this.productNameSnapshot=productNameSnapshot;this.contactName=contactName;
        this.contactEmail=contactEmail;this.contactPhone=contactPhone;this.message=message;
        this.consentAt=consentAt;this.requesterHash=requesterHash;this.createdAt=createdAt;
        this.leadStatus="NEW";this.updatedAt=createdAt;
    }

    public void assignTo(String username,String actor,Instant at) {
        this.assignedTo=username;
        this.updatedBy=actor;
        this.updatedAt=at;
    }
    public void changeLeadStatus(String status,String actor,Instant at) {
        this.leadStatus=status;
        this.updatedBy=actor;
        this.updatedAt=at;
    }

    public Long id(){return id;}
    public long tenantId(){return tenantId;}
    public Long productId(){return productId;}
    public String productNameSnapshot(){return productNameSnapshot;}
    public String contactName(){return contactName;}
    public String contactEmail(){return contactEmail;}
    public String contactPhone(){return contactPhone;}
    public String message(){return message;}
    public Instant createdAt(){return createdAt;}
    public String status(){return leadStatus;}
    public String assignedTo(){return assignedTo;}
    public String updatedBy(){return updatedBy;}
    public Instant updatedAt(){return updatedAt;}
    public long version(){return version;}
}
