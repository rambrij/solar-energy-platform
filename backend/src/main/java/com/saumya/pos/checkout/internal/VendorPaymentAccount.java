package com.saumya.pos.checkout.internal;

import jakarta.persistence.*;
import java.time.Instant;

/** Tenant-owned provider merchant reference; it never stores provider secrets or bank credentials. */
@Entity
@Table(name="vendor_payment_accounts",
    uniqueConstraints=@UniqueConstraint(name="uk_vendor_payment_account_tenant_provider",
        columnNames={"tenant_id","provider_key"}))
public class VendorPaymentAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="tenant_id",nullable=false,updatable=false) private long tenantId;
    @Column(name="provider_key",nullable=false,length=40,updatable=false) private String providerKey;
    @Column(name="merchant_account_reference",nullable=false,length=160) private String merchantAccountReference;
    @Column(nullable=false,length=24) private String status;
    @Column(name="verification_reference",length=160) private String verificationReference;
    @Column(name="verified_at") private Instant verifiedAt;
    @Column(name="rejection_reason",length=500) private String rejectionReason;
    @Column(name="submitted_by",nullable=false,length=100,updatable=false) private String submittedBy;
    @Column(name="updated_by",nullable=false,length=100) private String updatedBy;
    @Column(name="submitted_at",nullable=false,updatable=false) private Instant submittedAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;

    protected VendorPaymentAccount() {}

    public VendorPaymentAccount(long tenantId,String providerKey,String merchantAccountReference,
            String actor,Instant now) {
        this.tenantId=tenantId;
        this.providerKey=providerKey;
        this.merchantAccountReference=merchantAccountReference;
        this.status="PENDING_VERIFICATION";
        this.submittedBy=actor;
        this.updatedBy=actor;
        this.submittedAt=now;
        this.updatedAt=now;
    }

    public void resubmit(String merchantAccountReference,String actor,Instant now) {
        this.merchantAccountReference=merchantAccountReference;
        this.status="PENDING_VERIFICATION";
        this.verificationReference=null;
        this.verifiedAt=null;
        this.rejectionReason=null;
        this.updatedBy=actor;
        this.updatedAt=now;
    }

    /** Reserved for a future signature-verified provider adapter; there is no vendor-facing verify operation. */
    public void providerVerified(String providerReference,Instant now) {
        if(!"PENDING_VERIFICATION".equals(status)||providerReference==null||providerReference.isBlank()
                ||providerReference.length()>160||now==null)
            throw new IllegalStateException("A pending merchant account and provider verification reference are required");
        status="VERIFIED";
        verificationReference=providerReference;
        verifiedAt=now;
        rejectionReason=null;
        updatedAt=now;
    }

    /** Reserved for an authenticated provider review result; vendors cannot invoke this transition. */
    public void providerRejected(String reason,String actor,Instant now) {
        if(!"PENDING_VERIFICATION".equals(status)||reason==null||reason.isBlank()
                ||reason.length()>500||actor==null||actor.isBlank()||now==null)
            throw new IllegalStateException("A pending merchant account and provider review reason are required");
        status="REJECTED";
        rejectionReason=reason;
        updatedBy=actor;
        updatedAt=now;
    }

    public Long id(){return id;}
    public long tenantId(){return tenantId;}
    public String providerKey(){return providerKey;}
    public String merchantAccountReference(){return merchantAccountReference;}
    public String status(){return status;}
    public String verificationReference(){return verificationReference;}
    public Instant verifiedAt(){return verifiedAt;}
    public String rejectionReason(){return rejectionReason;}
    public String submittedBy(){return submittedBy;}
    public String updatedBy(){return updatedBy;}
    public Instant submittedAt(){return submittedAt;}
    public Instant updatedAt(){return updatedAt;}
    public long version(){return version;}
}
