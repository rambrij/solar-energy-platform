package com.saumya.pos.identity.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="identity_security_events")
public class IdentitySecurityEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(name="actor_type",nullable=false,length=16) private String actorType;
 @Column(nullable=false,length=64) private String action;
 @Column(name="revoked_refresh_token_count",nullable=false) private int revokedRefreshTokenCount;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 protected IdentitySecurityEvent(){}
 public IdentitySecurityEvent(long userId,String actorType,String action,int count,Instant occurredAt){
  if(userId<=0||!"CUSTOMER".equals(actorType)||!"CUSTOMER_OTHER_SESSIONS_REVOKED".equals(action)||count<0||occurredAt==null)throw new IllegalArgumentException("Invalid customer security event");
  this.userId=userId;this.actorType=actorType;this.action=action;this.revokedRefreshTokenCount=count;this.occurredAt=occurredAt;
 }
}
