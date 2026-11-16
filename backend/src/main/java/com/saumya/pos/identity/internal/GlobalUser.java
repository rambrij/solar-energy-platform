package com.saumya.pos.identity.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="users")
public class GlobalUser {
    @Id private Long id;
    @Column(nullable=false,unique=true,length=100) private String username;
    @Column(name="password_hash",nullable=false,length=200) private String password;
    @Column(nullable=false) private boolean enabled;
    @Column(name="platform_mfa_secret_enc",length=512) private String platformMfaSecretEnc;
    @Column(name="platform_mfa_enrolled_at") private Instant platformMfaEnrolledAt;
    @Column(name="platform_mfa_last_counter",nullable=false) private long platformMfaLastCounter=-1;
    protected GlobalUser(){}
    public GlobalUser(long id,String username,String password){this(id,username,password,true);}
    public GlobalUser(long id,String username,String password,boolean enabled){this.id=id;this.username=username;this.password=password;this.enabled=enabled;}
    public long id(){return id;} public String username(){return username;} public String password(){return password;} public boolean enabled(){return enabled;}
    public boolean platformMfaEnrolled(){return platformMfaSecretEnc!=null&&platformMfaEnrolledAt!=null;}
    public String platformMfaSecretEnc(){return platformMfaSecretEnc;}
    public long platformMfaLastCounter(){return platformMfaLastCounter;}
    public void activate(String passwordHash){this.password=passwordHash;this.enabled=true;}
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
