package com.saumya.pos.identity.internal;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="identity_email_verification_tokens",indexes={@Index(name="ix_identity_email_verification_user",columnList="user_id,created_at")})
public class IdentityEmailVerificationToken {
 @Id @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
 @Column(name="user_id",nullable=false) private long userId;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="consumed_at") private Instant consumedAt;
 @Column(name="cancelled_at") private Instant cancelledAt;
 protected IdentityEmailVerificationToken() {}
 public IdentityEmailVerificationToken(String hash,long userId,Instant created,Instant expires){
  if(hash==null||!hash.matches("[a-f0-9]{64}")||userId<=0||created==null||expires==null||!expires.isAfter(created)) throw new IllegalArgumentException("Valid verification token material required");
  tokenHash=hash;this.userId=userId;createdAt=created;expiresAt=expires;
 }
 public String tokenHash(){return tokenHash;} public long userId(){return userId;}
 public Instant createdAt(){return createdAt;} public Instant expiresAt(){return expiresAt;}
 public Instant consumedAt(){return consumedAt;} public Instant cancelledAt(){return cancelledAt;}
 public boolean usableAt(Instant now){return consumedAt==null&&cancelledAt==null&&expiresAt.isAfter(now);}
 public void consume(Instant now){if(!usableAt(now))throw new IllegalStateException("Email verification token is unavailable");consumedAt=now;}
 public void cancel(Instant now){if(consumedAt==null&&cancelledAt==null)cancelledAt=now;}
}
