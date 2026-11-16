package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="platform_audit_events")
public class PlatformAuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="actor_username",nullable=false,length=100) private String actor;
    @Column(nullable=false,length=80) private String action;
    @Column(name="resource_type",nullable=false,length=60) private String resourceType;
    @Column(name="resource_id",length=100) private String resourceId;
    @Column(name="tenant_id") private Long tenantId;
    @Column(length=500) private String reason;
    @Column(nullable=false,length=20) private String outcome;
    @Column(name="correlation_id",length=100) private String correlationId;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    protected PlatformAuditEvent() {}
    public PlatformAuditEvent(String actor,String action,String resourceType,String resourceId,Long tenantId,String reason,String outcome,String correlationId,Instant occurredAt){
        this.actor=actor;this.action=action;this.resourceType=resourceType;this.resourceId=resourceId;this.tenantId=tenantId;this.reason=reason;this.outcome=outcome;this.correlationId=correlationId;this.occurredAt=occurredAt;
    }
    public long id(){return id;} public String actor(){return actor;} public String action(){return action;} public String resourceType(){return resourceType;} public String resourceId(){return resourceId;} public Long tenantId(){return tenantId;} public String reason(){return reason;} public String outcome(){return outcome;} public String correlationId(){return correlationId;} public Instant occurredAt(){return occurredAt;}
}
