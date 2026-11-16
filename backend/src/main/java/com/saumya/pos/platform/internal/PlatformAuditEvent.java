package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only platform audit event. Immutable: Hibernate refuses updates and the module exposes no update or delete. */
@Entity @Table(name="platform_audit_events") @Immutable
public class PlatformAuditEvent {
    @Id @Column(length=36) private String id;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    @Column(nullable=false,length=100) private String actor;
    @Column(nullable=false,length=60) private String action;
    @Column(name="target_type",nullable=false,length=40) private String targetType;
    @Column(name="target_id",nullable=false,length=64) private String targetId;
    @Column(name="tenant_id") private Long tenantId;
    @Column(length=500) private String reason;
    @Column(length=2000) private String details;
    @Column(nullable=false,length=12) private String outcome;
    protected PlatformAuditEvent(){}
    public PlatformAuditEvent(String id,Instant at,String actor,String action,String targetType,String targetId,Long tenantId,String reason,String details,String outcome){
        this.id=id;occurredAt=at.truncatedTo(ChronoUnit.MICROS);this.actor=actor;this.action=action;this.targetType=targetType;this.targetId=targetId;this.tenantId=tenantId;this.reason=reason;this.details=details;this.outcome=outcome;
    }
    public String id(){return id;} public Instant occurredAt(){return occurredAt;} public String actor(){return actor;} public String action(){return action;} public String targetType(){return targetType;}
    public String targetId(){return targetId;} public Long tenantId(){return tenantId;} public String reason(){return reason;} public String details(){return details;} public String outcome(){return outcome;}
}
