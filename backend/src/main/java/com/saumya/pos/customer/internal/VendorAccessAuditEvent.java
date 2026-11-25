package com.saumya.pos.customer.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="vendor_access_audit_events")
public class VendorAccessAuditEvent {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="tenant_id",nullable=false)
    private long tenantId;
    @Column(name="actor_username",nullable=false,length=100)
    private String actorUsername;
    @Column(name="resource_type",nullable=false,length=40)
    private String resourceType;
    @Column(name="resource_id",length=120)
    private String resourceId;
    @Column(nullable=false,length=60)
    private String action;
    @Column(nullable=false,length=500)
    private String reason;
    @Column(name="occurred_at",nullable=false,updatable=false)
    private Instant occurredAt;

    protected VendorAccessAuditEvent() {}

    public VendorAccessAuditEvent(long tenantId,String actorUsername,String resourceType,
            String resourceId,String action,String reason) {
        this.tenantId=tenantId;
        this.actorUsername=actorUsername;
        this.resourceType=resourceType;
        this.resourceId=resourceId;
        this.action=action;
        this.reason=reason;
        this.occurredAt=Instant.now();
    }
}
