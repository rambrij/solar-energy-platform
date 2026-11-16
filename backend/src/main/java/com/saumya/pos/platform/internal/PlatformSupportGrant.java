package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="platform_support_access")
public class PlatformSupportGrant {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="actor_username",nullable=false,length=100) private String actor;
    @Column(nullable=false,length=500) private String purpose;
    @Column(name="ticket_reference",nullable=false,length=120) private String ticketReference;
    @Column(name="granted_at",nullable=false,updatable=false) private Instant grantedAt;
    @Column(name="expires_at",nullable=false,updatable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="revoked_by",length=100) private String revokedBy;
    protected PlatformSupportGrant() {}
    public PlatformSupportGrant(long tenantId,String actor,String purpose,String ticket,Instant grantedAt,Instant expiresAt){
        this.tenantId=tenantId;this.actor=actor;this.purpose=purpose;this.ticketReference=ticket;this.grantedAt=grantedAt;this.expiresAt=expiresAt;
    }
    public long id(){return id;} public long tenantId(){return tenantId;} public String actor(){return actor;} public String purpose(){return purpose;} public String ticketReference(){return ticketReference;} public Instant grantedAt(){return grantedAt;} public Instant expiresAt(){return expiresAt;} public Instant revokedAt(){return revokedAt;}
    public void revoke(String actor,Instant now){if(revokedAt!=null)return;revokedAt=now;revokedBy=actor;}
    public boolean activeAt(Instant now){return revokedAt==null&&expiresAt.isAfter(now);}
}
