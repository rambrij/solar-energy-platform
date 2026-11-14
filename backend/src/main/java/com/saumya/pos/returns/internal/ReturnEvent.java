package com.saumya.pos.returns.internal;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="pos_return_events") @org.hibernate.annotations.Immutable
public class ReturnEvent {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="return_id",nullable=false,length=36) private String returnId;
 @Column(nullable=false,length=100) private String actor;
 @Column(nullable=false,length=16) private String action;
 @Column(nullable=false,length=250) private String note;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 protected ReturnEvent(){}
 public ReturnEvent(long tenant,String id,String actor,String action,String note,Instant time){this.id=UUID.randomUUID().toString();this.tenant=tenant;returnId=id;this.actor=actor;this.action=action;this.note=note;created=time;}
}
