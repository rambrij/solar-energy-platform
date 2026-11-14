package com.saumya.pos.documents.internal;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @org.hibernate.annotations.Immutable @Table(name="receipt_document_audit")
public class ReceiptDocumentAudit {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id") private long tenant;
 @Column(name="branch_id") private long branch;
 @Column(name="order_id",length=36) private String order;
 @Column(length=100) private String actor;
 @Column(length=250) private String reason;
 @Column(name="content_sha256",length=64) private String digest;
 @Column(name="content_length") private int length;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at") private Instant created;
 protected ReceiptDocumentAudit(){}
 public ReceiptDocumentAudit(String id,long tenant,long branch,String order,String actor,String reason,String digest,int length,Instant created){this.id=id;this.tenant=tenant;this.branch=branch;this.order=order;this.actor=actor;this.reason=reason;this.digest=digest;this.length=length;this.created=created;}
}
