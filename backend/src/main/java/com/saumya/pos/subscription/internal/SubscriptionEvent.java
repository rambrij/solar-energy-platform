package com.saumya.pos.subscription.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="subscription_events")
public class SubscriptionEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="subscription_id",nullable=false) private Long subscriptionId;
    @Column(name="old_status",length=30) private String oldStatus;
    @Column(name="new_status",nullable=false,length=30) private String newStatus;
    @Column(name="actor_username",nullable=false,length=100) private String actor;
    @Column(nullable=false,length=500) private String reason;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    protected SubscriptionEvent(){}
    public SubscriptionEvent(long tenantId,long subId,String oldStatus,String newStatus,String actor,String reason,Instant occurredAt){
        this.tenantId=tenantId;this.subscriptionId=subId;this.oldStatus=oldStatus;this.newStatus=newStatus;this.actor=actor;this.reason=reason;this.occurredAt=occurredAt;
    }
    public long id(){return id;}public long tenantId(){return tenantId;}public long subscriptionId(){return subscriptionId;}public String oldStatus(){return oldStatus;}public String newStatus(){return newStatus;}public String actor(){return actor;}public String reason(){return reason;}public Instant occurredAt(){return occurredAt;}
}
