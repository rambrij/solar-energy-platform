package com.saumya.pos.retail.internal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="branch_settings_events")
public class BranchSettingsEvent {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(nullable=false,length=100) private String actor;
 @Column(nullable=false,length=250) private String reason;
 @Column(name="before_state",nullable=false,columnDefinition="TEXT") private String before;
 @Column(name="after_state",nullable=false,columnDefinition="TEXT") private String after;
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 protected BranchSettingsEvent(){}
 public BranchSettingsEvent(long tenant,long branch,String actor,String reason,String before,String after,Instant created){id=java.util.UUID.randomUUID().toString();this.tenant=tenant;this.branch=branch;this.actor=actor;this.reason=reason;this.before=before;this.after=after;this.created=created;}
}
