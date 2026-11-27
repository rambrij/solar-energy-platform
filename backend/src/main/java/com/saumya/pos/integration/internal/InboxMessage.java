package com.saumya.pos.integration.internal;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="integration_inbox",uniqueConstraints=@UniqueConstraint(name="uq_integration_inbox_consumer_event",columnNames={"consumer_key","provider_event_id"}))
public class InboxMessage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="consumer_key",nullable=false,length=100) private String consumerKey;
 @Column(name="provider_event_id",nullable=false,length=160) private String providerEventId;
 @Column(name="tenant_id") private Long tenantId;
 @Column(name="event_type",nullable=false,length=120) private String eventType;
 @Column(name="payload_json",nullable=false,columnDefinition="TEXT") private String payloadJson;
 @Column(nullable=false,length=16) private String state;
 @Column(name="duplicate_count",nullable=false) private int duplicateCount;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="received_at",nullable=false) private Instant receivedAt;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="processed_at") private Instant processedAt;
 protected InboxMessage(){}
 public InboxMessage(String consumer,String provider,Long tenant,String type,String payload,Instant at){consumerKey=consumer;providerEventId=provider;tenantId=tenant;eventType=type;payloadJson=payload;receivedAt=at;state="PROCESSED";processedAt=at;}
 public void duplicate(){duplicateCount++;}
 public Long tenantId(){return tenantId;} public String eventType(){return eventType;} public String payloadJson(){return payloadJson;} public String state(){return state;} public int duplicateCount(){return duplicateCount;}
}