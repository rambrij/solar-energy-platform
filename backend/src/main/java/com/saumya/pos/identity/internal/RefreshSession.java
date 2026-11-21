package com.saumya.pos.identity.internal;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="identity_refresh_tokens")
public class RefreshSession {
 @Id @Column(name="token_hash",length=64) private String hash;
 @Column(nullable=false,length=100) private String username;
 @Column(nullable=false,length=36) private String family;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="expires_at",nullable=false) private Instant expires;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="used_at") private Instant used;
 @Column(nullable=false) private boolean revoked;
 @Column(name="mfa_verified",nullable=false) private boolean mfaVerified;
 @Column(name="actor_type",nullable=false,length=16) private String actorType;
 @Column(name="tenant_id") private Long tenantId;
 @Column(name="membership_id",length=220) private String membershipId;

 protected RefreshSession(){}
 public RefreshSession(String hash,String username,String family,Instant expires){this(hash,username,family,expires,false,"UNKNOWN",null,null);}
 public RefreshSession(String hash,String username,String family,Instant expires,boolean mfaVerified){this(hash,username,family,expires,mfaVerified,"UNKNOWN",null,null);}
 public RefreshSession(String hash,String username,String family,Instant expires,boolean mfaVerified,String actorType,Long tenantId,String membershipId){
  this.hash=hash;this.username=username;this.family=family;this.expires=expires;this.mfaVerified=mfaVerified;
  this.actorType=actorType;this.tenantId=tenantId;this.membershipId=membershipId;
 }
 public String username(){return username;}
 public String family(){return family;}
 public Instant expires(){return expires;}
 public boolean spent(){return revoked||used!=null;}
 public boolean mfaVerified(){return mfaVerified;}
 public String actorType(){return actorType;}
 public Long tenantId(){return tenantId;}
 public String membershipId(){return membershipId;}
 public void consume(Instant now){used=now;}
}
