package com.saumya.pos.returns.internal;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="pos_returns")
public class ReturnCase {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(name="order_id",nullable=false,length=36) private String orderId;
 @Column(nullable=false,length=100) private String requester;
 @Column(name="session_id",nullable=false,length=36) private String session;
 @Column(name="idempotency_key",nullable=false,length=80) private String key;
 @Column(name="request_hash",nullable=false,length=64) private String hash;
 @Column(nullable=false,length=250) private String reason;
 @Column(nullable=false,length=32) private String proof;
 @Column(name="proof_note",nullable=false,length=250) private String proofNote;
 @Column(nullable=false,length=16) private String state;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 @Column(name="reviewed_by",length=100) private String reviewer;
 @Column(name="review_reason",length=250) private String reviewReason;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="reviewed_at") private Instant reviewed;
 @Column(name="inspected_by",length=100) private String inspector;
 @Column(name="inspection_hash",length=64) private String inspectionHash;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="inspected_at") private Instant inspected;
 protected ReturnCase(){}
 public ReturnCase(String id,long tenant,long branch,String order,String requester,String session,String key,String hash,String reason,String proof,String note,Instant time){this.id=id;this.tenant=tenant;this.branch=branch;orderId=order;this.requester=requester;this.session=session;this.key=key;this.hash=hash;this.reason=reason;this.proof=proof;proofNote=note;created=time;state="REQUESTED";}
 public String id(){return id;} public long tenant(){return tenant;} public long branch(){return branch;} public String order(){return orderId;} public String requester(){return requester;} public String hash(){return hash;} public String state(){return state;} public String reason(){return reason;} public String proof(){return proof;} public String proofNote(){return proofNote;} public Instant created(){return created;} public String reviewer(){return reviewer;} public String reviewReason(){return reviewReason;} public String inspector(){return inspector;} public String inspectionHash(){return inspectionHash;}
 public void review(String state,String actor,String reason,Instant time){this.state=state;reviewer=actor;reviewReason=reason;reviewed=time;}
 public void inspect(String actor,String hash,Instant time){state="INSPECTED";inspector=actor;inspectionHash=hash;inspected=time;}
}
