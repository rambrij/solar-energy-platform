package com.saumya.pos.reporting.internal;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="report_export_audit") @org.hibernate.annotations.Immutable
public class ReportExportAudit {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(nullable=false,length=100) private String actor;
 @Column(name="report_kind",nullable=false,length=24) private String kind;
 @Column(name="from_date",nullable=false) private LocalDate from;
 @Column(name="to_date",nullable=false) private LocalDate to;
 @Column(nullable=false,length=16) private String channel;
 @Column(name="row_count",nullable=false) private int rows;
 @Column(name="content_sha256",nullable=false,length=64) private String digest;
 @Column(nullable=false,length=250) private String reason;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 protected ReportExportAudit(){}
 public ReportExportAudit(String id,long tenant,long branch,String actor,String kind,LocalDate from,LocalDate to,String channel,int rows,String digest,String reason,Instant created){this.id=id;this.tenant=tenant;this.branch=branch;this.actor=actor;this.kind=kind;this.from=from;this.to=to;this.channel=channel;this.rows=rows;this.digest=digest;this.reason=reason;this.created=created;}
 public String id(){return id;} public String digest(){return digest;} public int rows(){return rows;}
}
