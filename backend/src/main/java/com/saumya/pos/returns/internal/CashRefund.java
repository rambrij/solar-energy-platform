package com.saumya.pos.returns.internal;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="pos_cash_refunds") @org.hibernate.annotations.Immutable
public class CashRefund {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(name="return_id",nullable=false,length=36) private String returnId;
 @Column(name="order_id",nullable=false,length=36) private String orderId;
 @Column(name="original_receipt",nullable=false) private long receipt;
 @Column(nullable=false,length=100) private String cashier;
 @Column(name="return_requester",nullable=false,length=100) private String requester;
 @Column(name="session_id",nullable=false,length=36) private String session;
 @Column(name="idempotency_key",nullable=false,length=80) private String key;
 @Column(name="request_hash",nullable=false,length=64) private String hash;
 @Column(name="quote_hash",nullable=false,length=64) private String quoteHash;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(nullable=false,length=250) private String reason;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="paid_at",nullable=false) private Instant paid;
 protected CashRefund(){}
 public CashRefund(String id,long tenant,long branch,String returned,String order,long receipt,String cashier,String requester,String session,String key,String hash,String quote,BigDecimal amount,String reason,Instant paid){this.id=id;this.tenant=tenant;this.branch=branch;returnId=returned;orderId=order;this.receipt=receipt;this.cashier=cashier;this.requester=requester;this.session=session;this.key=key;this.hash=hash;quoteHash=quote;this.amount=amount;this.reason=reason;this.paid=paid;}
 public long receipt(){return receipt;}
 public String id(){return id;} public long tenant(){return tenant;} public long branch(){return branch;} public String returned(){return returnId;} public String order(){return orderId;} public String cashier(){return cashier;} public String session(){return session;} public String hash(){return hash;} public BigDecimal amount(){return amount;} public String reason(){return reason;} public Instant paid(){return paid;}
}
