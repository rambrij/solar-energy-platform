package com.saumya.pos.identity.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="identity_invitations")
public class IdentityInvitation {
    @Id @Column(name="token_hash",length=64) private String tokenHash;
    @Column(nullable=false,length=100) private String username;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="consumed_at") private Instant consumedAt;
    protected IdentityInvitation() {}
    public IdentityInvitation(String hash,String username,Instant expiresAt,Instant createdAt){this.tokenHash=hash;this.username=username;this.expiresAt=expiresAt;this.createdAt=createdAt;}
    public String username(){return username;} public Instant expiresAt(){return expiresAt;} public Instant consumedAt(){return consumedAt;}
    public boolean usableAt(Instant now){return consumedAt==null&&expiresAt.isAfter(now);}
    public void consume(Instant now){if(consumedAt!=null)throw new IllegalStateException("Invitation already consumed");consumedAt=now;}
}
