package com.saumya.pos.tenant.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="tenant_profile_audit_events",indexes=@Index(name="ix_tenant_profile_audit_timeline",columnList="tenant_id,occurred_at,id"))
public class TenantProfileAuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private long tenantId;
    @Column(name="actor_username",nullable=false,length=100) private String actorUsername;
    @Column(nullable=false,length=24) private String action;
    @Column(name="changed_fields",nullable=false,length=255) private String changedFields;
    @Column(name="before_version",nullable=false) private long beforeVersion;
    @Column(name="after_version",nullable=false) private long afterVersion;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    protected TenantProfileAuditEvent() {}
    public TenantProfileAuditEvent(long tenantId,String actorUsername,String action,String changedFields,long beforeVersion,long afterVersion,Instant occurredAt) {
        if(tenantId<=0||actorUsername==null||actorUsername.isBlank()||action==null||action.isBlank()||changedFields==null||changedFields.isBlank()||beforeVersion<0||afterVersion<beforeVersion||occurredAt==null) throw new IllegalArgumentException("Valid tenant profile audit material required");
        this.tenantId=tenantId;this.actorUsername=actorUsername;this.action=action;this.changedFields=changedFields;this.beforeVersion=beforeVersion;this.afterVersion=afterVersion;this.occurredAt=occurredAt;
    }
    public long tenantId(){return tenantId;} public String actorUsername(){return actorUsername;} public String action(){return action;}
    public String changedFields(){return changedFields;} public long beforeVersion(){return beforeVersion;} public long afterVersion(){return afterVersion;} public Instant occurredAt(){return occurredAt;}
}
