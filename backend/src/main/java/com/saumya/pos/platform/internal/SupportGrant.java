package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An explicit, purpose-bound, time-limited support-access grant. Only revocation may change it after creation. */
@Entity @Table(name="support_access_grants")
public class SupportGrant {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenant;
    @Column(name="granted_to",nullable=false,length=100) private String grantedTo;
    @Column(nullable=false,length=30) private String scope;
    @Column(nullable=false,length=500) private String reason;
    @Column(name="ticket_ref",nullable=false,length=60) private String ticket;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="granted_at",nullable=false) private Instant grantedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="revoked_by",length=100) private String revokedBy;
    protected SupportGrant(){}
    public SupportGrant(String id,long tenant,String grantedTo,String scope,String reason,String ticket,Instant granted,Instant expires){
        this.id=id;this.tenant=tenant;this.grantedTo=grantedTo;this.scope=scope;this.reason=reason;this.ticket=ticket;grantedAt=granted.truncatedTo(ChronoUnit.MICROS);expiresAt=expires.truncatedTo(ChronoUnit.MICROS);
    }
    public String id(){return id;} public long tenant(){return tenant;} public String grantedTo(){return grantedTo;} public String scope(){return scope;} public String reason(){return reason;} public String ticket(){return ticket;}
    public Instant grantedAt(){return grantedAt;} public Instant expiresAt(){return expiresAt;} public Instant revokedAt(){return revokedAt;} public String revokedBy(){return revokedBy;}
    public void revoke(String actor,Instant now){revokedBy=actor;revokedAt=now.truncatedTo(ChronoUnit.MICROS);}
}
