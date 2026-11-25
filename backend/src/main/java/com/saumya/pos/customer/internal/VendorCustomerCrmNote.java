package com.saumya.pos.customer.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="vendor_customer_crm_notes")
public class VendorCustomerCrmNote {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="tenant_id",nullable=false) private long tenantId;
    @Column(name="tenant_customer_id",nullable=false,length=36) private String relationshipId;
    @Column(name="note_text",nullable=false,length=2000) private String noteText;
    @Column(name="created_by",nullable=false,length=100) private String createdBy;
    @Column(name="updated_by",nullable=false,length=100) private String updatedBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(name="version",nullable=false) private long version;

    protected VendorCustomerCrmNote() {}
    public VendorCustomerCrmNote(long tenantId,String relationshipId,String noteText,String actor) {
        this.tenantId=tenantId; this.relationshipId=relationshipId; this.noteText=noteText;
        this.createdBy=actor; this.updatedBy=actor;
    }
    @PrePersist void beforeInsert() {
        Instant now=Instant.now(); if(createdAt==null)createdAt=now; if(updatedAt==null)updatedAt=now;
    }
    @PreUpdate void beforeUpdate() { updatedAt=Instant.now(); }
    public void update(String value,String actor) { noteText=value; updatedBy=actor; }
    public Long id(){return id;}
    public String relationshipId(){return relationshipId;}
    public String noteText(){return noteText;}
    public String createdBy(){return createdBy;}
    public String updatedBy(){return updatedBy;}
    public Instant createdAt(){return createdAt;}
    public Instant updatedAt(){return updatedAt;}
    public long version(){return version;}
}
