package com.saumya.pos.tenant.internal;

import com.saumya.pos.tenant.TenantStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id private Long id;
    @Column(nullable = false, unique = true, length = 100) private String slug;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "legal_name", nullable = false, length = 180) private String legalName;
    @Column(name = "contact_email", length = 100) private String contactEmail;
    @Column(name = "contact_phone", length = 30) private String contactPhone;
    @Column(name = "tax_identifier", length = 40) private String taxIdentifier;
    @Column(length = 500) private String address;
    @Column(name = "initial_vendor_admin_username", length = 100) private String initialVendorAdminUsername;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_status", nullable = false, length = 20) private TenantStatus lifecycleStatus;
    @Column(name = "online_branch_id") private Long onlineBranchId;
    @Column(name = "terms_accepted_at") private Instant termsAcceptedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    protected Tenant() {}
    public Tenant(long id,String slug,String name,String legalName,String contactEmail,String contactPhone,String taxIdentifier,String address,String admin,Instant termsAt,Instant now) {
        this.id=id;this.slug=slug;this.name=name;this.legalName=legalName;this.contactEmail=contactEmail;this.contactPhone=contactPhone;this.taxIdentifier=taxIdentifier;this.address=address;this.initialVendorAdminUsername=admin;this.termsAcceptedAt=termsAt;this.lifecycleStatus=TenantStatus.PENDING;this.createdAt=now;this.updatedAt=now;
    }
    public long id(){return id;} public String slug(){return slug;} public String name(){return name;} public String legalName(){return legalName;} public String contactEmail(){return contactEmail;} public String contactPhone(){return contactPhone;} public String taxIdentifier(){return taxIdentifier;} public String address(){return address;} public String initialVendorAdminUsername(){return initialVendorAdminUsername;} public TenantStatus lifecycleStatus(){return lifecycleStatus;} public boolean active(){return lifecycleStatus==TenantStatus.ACTIVE;} public Long onlineBranchId(){return onlineBranchId;} public Instant termsAcceptedAt(){return termsAcceptedAt;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;} public long version(){return version;}
    public void updateProfile(String name,String legal,String email,String phone,String tax,String address,Instant now){if(lifecycleStatus==TenantStatus.CLOSED)throw new IllegalStateException("Closed tenants cannot be edited");this.name=name;this.legalName=legal;this.contactEmail=email;this.contactPhone=phone;this.taxIdentifier=tax;this.address=address;this.updatedAt=now;}
    public void recordTermsAcceptance(Instant now){if(termsAcceptedAt==null)termsAcceptedAt=now;}
    public boolean transitionTo(TenantStatus next,Instant now){if(lifecycleStatus==next)return false;boolean allowed=switch(lifecycleStatus){case PENDING->next==TenantStatus.UNDER_REVIEW||next==TenantStatus.CLOSED;case UNDER_REVIEW->next==TenantStatus.ACTIVE||next==TenantStatus.CLOSED;case ACTIVE->next==TenantStatus.SUSPENDED||next==TenantStatus.CLOSED;case SUSPENDED->next==TenantStatus.ACTIVE||next==TenantStatus.CLOSED;case CLOSED->false;};if(!allowed)throw new IllegalStateException("Invalid tenant lifecycle transition: "+lifecycleStatus+" -> "+next);lifecycleStatus=next;updatedAt=now;return true;}
    public void assignOnlineBranch(long branchId,Instant now){if(lifecycleStatus==TenantStatus.CLOSED)throw new IllegalStateException("Closed tenants cannot receive an online branch");onlineBranchId=branchId;updatedAt=now;}
    public boolean matchesOnboarding(String requestedName,String requestedLegalName,String requestedAdmin){return name.equals(requestedName)&&legalName.equals(requestedLegalName)&&initialVendorAdminUsername.equals(requestedAdmin);}
}
