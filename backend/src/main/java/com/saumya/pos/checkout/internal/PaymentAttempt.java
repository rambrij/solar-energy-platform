package com.saumya.pos.checkout.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="vendor_payment_attempts")
public class PaymentAttempt {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenant;
    @Column(name="order_id",nullable=false,length=36) private String order;
    @Column(nullable=false,length=8) private String method;
    @Column(name="merchant_account",nullable=false,length=80) private String merchant;
    @Column(nullable=false,length=48) private String state;
    @Column(nullable=false,length=24) private String origin="ONLINE_MOCK";
    @Column(name="evidence_reference",length=100) private String evidence;
    @Column(name="reviewed_by",length=100) private String reviewer;
    @Column(name="review_reason",length=250) private String reason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(name="provider_reference",length=80) private String reference;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="confirmed_at") private Instant confirmed;
    @Column(name="idempotency_key",nullable=false,length=80) private String key;
    @Column(name="request_hash",nullable=false,length=64) private String hash;
    @Column(name="cash_received",precision=19,scale=2) private BigDecimal cash;
    @Column(name="change_given",precision=19,scale=2) private BigDecimal change;
    public String key(){return key;} public String hash(){return hash;}
    public BigDecimal cash(){return cash==null?BigDecimal.ZERO:cash;} public BigDecimal change(){return change==null?BigDecimal.ZERO:change;}
    public void command(String key,String hash){this.key=key;this.hash=hash;}
    public static PaymentAttempt cash(long tenant,String order,BigDecimal applied,BigDecimal received,String key,String hash,Instant now){var a=new PaymentAttempt(tenant,order,"CASH",applied,now);a.origin="POS_CASH";a.merchant="vendor-cash-"+tenant;a.state="CONFIRMED";a.cash=received;a.change=received.subtract(applied);a.confirmed=now;a.command(key,hash);return a;}
    protected PaymentAttempt(){}
    public PaymentAttempt(long tenant,String order,String method,BigDecimal amount,Instant now){this.id=java.util.UUID.randomUUID().toString();this.key=this.id;this.hash=this.id;this.tenant=tenant;this.order=order;this.method=method;this.amount=amount;this.merchant="mock-vendor-"+tenant;state="PENDING";created=now;}
    public String id(){return id;} public String order(){return order;} public String method(){return method;} public String merchant(){return merchant;} public String state(){return state;} public BigDecimal amount(){return amount;}
    public void confirm(Instant now,String reference){state="CONFIRMED";confirmed=now;this.reference=reference;}
    public static PaymentAttempt terminal(long tenant,String order,String method,String merchant,BigDecimal amount,Instant now){var a=new PaymentAttempt(tenant,order,method,amount,now);a.origin="POS_TERMINAL";a.merchant=merchant;a.state="PENDING_VERIFICATION";return a;}
    public boolean terminal(){return "POS_TERMINAL".equals(origin);}
    public boolean unresolved(){return terminal()&&java.util.Set.of("PENDING_VERIFICATION","UNKNOWN_RECONCILIATION_REQUIRED").contains(state);}
    public void unknown(){state="UNKNOWN_RECONCILIATION_REQUIRED";}
    public String reference(){return reference;} public String reviewer(){return reviewer;} public String evidence(){return evidence;} public String reason(){return reason;}
    public Instant reviewedAt(){return reviewedAt;}
    public void review(String actor,String decision,String reference,String evidence,String reason,Instant now){this.reviewer=actor;this.reference=reference;this.evidence=evidence;this.reason=reason;reviewedAt=now;state=decision.equals("CONFIRM")?"CONFIRMED":"FAILED";confirmed=decision.equals("CONFIRM")?now:null;}
}
