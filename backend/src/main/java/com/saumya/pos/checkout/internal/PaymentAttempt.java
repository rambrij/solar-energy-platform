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
    @Column(nullable=false,length=16) private String state;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(name="provider_reference",length=80) private String reference;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="confirmed_at") private Instant confirmed;
    protected PaymentAttempt(){}
    public PaymentAttempt(long tenant,String order,String method,BigDecimal amount,Instant now){this.id=java.util.UUID.randomUUID().toString();this.tenant=tenant;this.order=order;this.method=method;this.amount=amount;this.merchant="mock-vendor-"+tenant;state="PENDING";created=now;}
    public String id(){return id;} public String order(){return order;} public String method(){return method;} public String merchant(){return merchant;} public String state(){return state;} public BigDecimal amount(){return amount;}
    public void confirm(Instant now,String reference){state="CONFIRMED";confirmed=now;this.reference=reference;}
}
