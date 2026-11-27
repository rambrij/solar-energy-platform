package com.saumya.pos.checkout.internal;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only audit evidence for merchant-account onboarding and provider verification. */
@Entity
@Table(name="vendor_payment_account_events",
    indexes=@Index(name="ix_vendor_payment_account_event_timeline",
        columnList="tenant_id,account_id,occurred_at,id"))
public class VendorPaymentAccountEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false,updatable=false) private long tenantId;
    @Column(name="account_id",nullable=false,updatable=false) private long accountId;
    @Column(name="actor_username",nullable=false,length=100,updatable=false) private String actorUsername;
    @Column(nullable=false,length=32,updatable=false) private String action;
    @Column(nullable=false,length=500,updatable=false) private String reason;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;

    protected VendorPaymentAccountEvent() {}
    public VendorPaymentAccountEvent(long tenantId,long accountId,String actorUsername,
            String action,String reason,Instant occurredAt) {
        if(tenantId<=0||accountId<=0||actorUsername==null||actorUsername.isBlank()
                ||action==null||!java.util.Set.of("SUBMITTED","RESUBMITTED","PROVIDER_VERIFIED","PROVIDER_REJECTED").contains(action)
                ||reason==null||reason.isBlank()||reason.length()>500||occurredAt==null)
            throw new IllegalArgumentException("Valid merchant-account audit event is required");
        this.tenantId=tenantId;this.accountId=accountId;this.actorUsername=actorUsername;
        this.action=action;this.reason=reason;this.occurredAt=occurredAt;
    }
    public long tenantId(){return tenantId;}
    public long accountId(){return accountId;}
    public String actorUsername(){return actorUsername;}
    public String action(){return action;}
    public String reason(){return reason;}
    public Instant occurredAt(){return occurredAt;}
}
