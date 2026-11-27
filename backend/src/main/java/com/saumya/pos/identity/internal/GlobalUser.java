package com.saumya.pos.identity.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="users")
public class GlobalUser {
    @Id private Long id;
    @Column(nullable=false,unique=true,length=100) private String username;
    @Column(name="password_hash",nullable=false,length=200) private String password;
    @Column(nullable=false) private boolean enabled;
    @Column(name="email_verified",nullable=false) private boolean emailVerified;
    @Column(name="platform_mfa_secret_enc",length=512) private String platformMfaSecretEnc;
    @Column(name="platform_mfa_enrolled_at") private Instant platformMfaEnrolledAt;
    @Column(name="platform_mfa_last_counter",nullable=false) private long platformMfaLastCounter=-1;
    @Column(name="platform_mfa_failed_attempts",nullable=false) private int platformMfaFailedAttempts;
    @Column(name="platform_mfa_locked_until") private Instant platformMfaLockedUntil;
    protected GlobalUser(){}
    public GlobalUser(long id,String username,String password){this(id,username,password,true,true);}
    public GlobalUser(long id,String username,String password,boolean enabled){this(id,username,password,enabled,enabled);}
    public GlobalUser(long id,String username,String password,boolean enabled,boolean emailVerified){this.id=id;this.username=username;this.password=password;this.enabled=enabled;this.emailVerified=emailVerified;}
    public long id(){return id;} public String username(){return username;} public String password(){return password;} public boolean enabled(){return enabled;} public boolean emailVerified(){return emailVerified;}
    public boolean platformMfaEnrolled(){return platformMfaSecretEnc!=null&&platformMfaEnrolledAt!=null;}
    public String platformMfaSecretEnc(){return platformMfaSecretEnc;}
    public long platformMfaLastCounter(){return platformMfaLastCounter;}
    public boolean platformMfaLocked(Instant now){return platformMfaLockedUntil!=null&&platformMfaLockedUntil.isAfter(now);}
    public int platformMfaFailedAttempts(){return platformMfaFailedAttempts;}
    public Instant platformMfaLockedUntil(){return platformMfaLockedUntil;}
    public void platformMfaFailure(Instant now){
        platformMfaFailedAttempts++;
        if(platformMfaFailedAttempts>=5){platformMfaFailedAttempts=0;platformMfaLockedUntil=now.plusSeconds(900);}
    }
    public void platformMfaSuccess(){platformMfaFailedAttempts=0;platformMfaLockedUntil=null;}
    public void resetPlatformMfa(){
        this.platformMfaSecretEnc=null;this.platformMfaEnrolledAt=null;this.platformMfaLastCounter=-1;
        this.platformMfaFailedAttempts=0;this.platformMfaLockedUntil=null;
    }
    public void activate(String passwordHash){this.password=passwordHash;this.enabled=true;this.emailVerified=true;}
    public void verifyEmail(){this.emailVerified=true;}
    public void enrollPlatformMfa(String encryptedSecret,Instant now,long counter){
        if(encryptedSecret==null||encryptedSecret.isBlank()||now==null||counter<0)throw new IllegalArgumentException("Valid platform MFA material required");
        this.platformMfaSecretEnc=encryptedSecret;this.platformMfaEnrolledAt=now;this.platformMfaLastCounter=counter;
    }
    public void acceptPlatformMfaCounter(long counter){
        if(!platformMfaEnrolled()||counter<=platformMfaLastCounter)throw new IllegalStateException("Platform MFA code was already used");
        this.platformMfaLastCounter=counter;
    }
    public void disable(){this.enabled=false;}
}
