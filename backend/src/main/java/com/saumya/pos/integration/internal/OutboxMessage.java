package com.saumya.pos.integration.internal;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="integration_outbox")
public class OutboxMessage {
 @Id @Column(name="event_id",length=36) private String eventId;
 @Column(name="tenant_id") private Long tenantId;
 @Column(nullable=false,length=100) private String actor;
 @Column(name="aggregate_type",nullable=false,length=80) private String aggregateType;
 @Column(name="aggregate_id",nullable=false,length=100) private String aggregateId;
 @Column(name="event_type",nullable=false,length=120) private String eventType;
 @Column(name="schema_version",nullable=false) private int schemaVersion;
 @Column(name="correlation_id",nullable=false,length=80) private String correlationId;
 @Column(name="payload_json",nullable=false,columnDefinition="TEXT") private String payloadJson;
 @Column(nullable=false,length=16) private String state;
 @Column(name="attempt_count",nullable=false) private int attemptCount;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="available_at",nullable=false) private Instant availableAt;
 @Column(name="lease_owner",length=36) private String leaseOwner;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="lease_until") private Instant leaseUntil;
 @Column(name="last_error",length=120) private String lastError;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant createdAt;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="published_at") private Instant publishedAt;
 protected OutboxMessage(){}
 public OutboxMessage(String id,Long tenant,String actor,String aggregate,String aggregateId,String type,String correlation,String payload,Instant now){
  eventId=id;tenantId=tenant;this.actor=actor;aggregateType=aggregate;this.aggregateId=aggregateId;eventType=type;schemaVersion=1;correlationId=correlation;payloadJson=payload;state="PENDING";availableAt=now;createdAt=now;
 }
 public void claim(String worker,Instant until){state="PROCESSING";leaseOwner=worker;leaseUntil=until;attemptCount++;}
 public void published(Instant at){state="PUBLISHED";publishedAt=at;leaseOwner=null;leaseUntil=null;lastError=null;}
 public void failed(Instant retry,String error,int max){state=attemptCount>=max?"DEAD":"PENDING";availableAt=retry;leaseOwner=null;leaseUntil=null;lastError=error;}
 public String eventId(){return eventId;} public Long tenantId(){return tenantId;} public String actor(){return actor;} public String aggregateType(){return aggregateType;} public String aggregateId(){return aggregateId;} public String eventType(){return eventType;} public int schemaVersion(){return schemaVersion;} public String correlationId(){return correlationId;} public String payloadJson(){return payloadJson;} public int attemptCount(){return attemptCount;} public String state(){return state;} public Instant createdAt(){return createdAt;}
}