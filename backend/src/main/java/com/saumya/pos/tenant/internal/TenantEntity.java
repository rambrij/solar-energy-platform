package com.saumya.pos.tenant.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The tenant (vendor) aggregate: profile and lifecycle. Owned by the tenant module; other modules use TenantLifecycleService. */
@Entity @Table(name="tenants")
public class TenantEntity {
    @Id private Long id;
    @Column(length=100) private String slug;
    @Column(nullable=false,length=160) private String name;
    @Column(name="legal_name",length=200) private String legalName;
    @Column(name="contact_email",length=200) private String contactEmail;
    @Column(name="contact_phone",length=40) private String contactPhone;
    @Column(length=400) private String address;
    @Column(name="tax_id",length=40) private String taxId;
    @Column(nullable=false,length=16) private String status;
    @Column(name="status_reason",length=500) private String statusReason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="status_changed_at") private Instant statusChangedAt;
    @Column(name="status_changed_by",length=100) private String statusChangedBy;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at") private Instant createdAt;
    protected TenantEntity(){}
    public TenantEntity(long id,String slug,String name,String legalName,String contactEmail,String contactPhone,String address,String taxId,String status,Instant now){
        this.id=id;this.slug=slug;this.name=name;this.legalName=legalName;this.contactEmail=contactEmail;this.contactPhone=contactPhone;this.address=address;this.taxId=taxId;this.status=status;createdAt=now.truncatedTo(ChronoUnit.MICROS);
    }
    public Long id(){return id;} public String slug(){return slug;} public String name(){return name;} public String legalName(){return legalName;}
    public String contactEmail(){return contactEmail;} public String contactPhone(){return contactPhone;} public String address(){return address;} public String taxId(){return taxId;}
    public String status(){return status;} public String statusReason(){return statusReason;} public Instant statusChangedAt(){return statusChangedAt;} public String statusChangedBy(){return statusChangedBy;} public Instant createdAt(){return createdAt;}
    public void profile(String name,String legalName,String contactEmail,String contactPhone,String address,String taxId){this.name=name;this.legalName=legalName;this.contactEmail=contactEmail;this.contactPhone=contactPhone;this.address=address;this.taxId=taxId;}
    public void status(String status,String reason,String actor,Instant now){this.status=status;statusReason=reason;statusChangedBy=actor;statusChangedAt=now.truncatedTo(ChronoUnit.MICROS);}
}
