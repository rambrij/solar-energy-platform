package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** SaaS subscription payment received into the platform merchant account. Never shares tables or ids with vendor purchase payments. */
@Entity @Table(name="subscription_payments") @Immutable
public class SubscriptionPayment {
    @Id @Column(length=36) private String id;
    @Column(name="invoice_id",nullable=false,length=36) private String invoiceId;
    @Column(name="tenant_id",nullable=false) private long tenant;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=10) private String method;
    @Column(nullable=false,length=10) private String status;
    @Column(name="platform_merchant_id",nullable=false,length=80) private String merchantId;
    @Column(name="provider_reference",length=120) private String reference;
    @Column(name="recorded_by",nullable=false,length=100) private String recordedBy;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="recorded_at",nullable=false) private Instant recordedAt;
    protected SubscriptionPayment(){}
    public SubscriptionPayment(String id,String invoiceId,long tenant,BigDecimal amount,String currency,String method,String status,String merchantId,String reference,String actor,Instant now){
        this.id=id;this.invoiceId=invoiceId;this.tenant=tenant;this.amount=amount;this.currency=currency;this.method=method;this.status=status;this.merchantId=merchantId;this.reference=reference;recordedBy=actor;recordedAt=now.truncatedTo(ChronoUnit.MICROS);
    }
    public String id(){return id;} public String invoiceId(){return invoiceId;} public BigDecimal amount(){return amount;} public String currency(){return currency;} public String method(){return method;} public String status(){return status;}
    public String merchantId(){return merchantId;} public String reference(){return reference;} public String recordedBy(){return recordedBy;} public Instant recordedAt(){return recordedAt;}
}
