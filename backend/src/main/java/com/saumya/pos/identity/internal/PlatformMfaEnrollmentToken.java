package com.saumya.pos.identity.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="platform_mfa_enrollment_tokens")
public class PlatformMfaEnrollmentToken {
    @Id @Column(name="token_hash",nullable=false,length=64)
    private String tokenHash;
    @Column(nullable=false,length=100)
    private String username;
    @Column(name="expires_at",nullable=false)
    private Instant expiresAt;
    @Column(name="pending_secret_enc",length=512)
    private String pendingSecretEnc;
    @Column(name="pending_expires_at")
    private Instant pendingExpiresAt;
    @Column(name="created_at",nullable=false)
    private Instant createdAt;

    protected PlatformMfaEnrollmentToken(){}
    public PlatformMfaEnrollmentToken(String tokenHash,String username,Instant expiresAt,Instant createdAt){
        this.tokenHash=tokenHash;this.username=username;this.expiresAt=expiresAt;this.createdAt=createdAt;
    }
    public String username(){return username;}
    public String pendingSecretEnc(){return pendingSecretEnc;}
    public boolean usableAt(Instant now){return expiresAt!=null&&expiresAt.isAfter(now);}
    public boolean pendingAt(Instant now){return pendingSecretEnc!=null&&pendingExpiresAt!=null&&pendingExpiresAt.isAfter(now);}
    public void beginPending(String encryptedSecret,Instant until){
        if(encryptedSecret==null||encryptedSecret.isBlank()||until==null)throw new IllegalArgumentException("Encrypted TOTP material and expiry required");
        this.pendingSecretEnc=encryptedSecret;this.pendingExpiresAt=until;
    }
}
