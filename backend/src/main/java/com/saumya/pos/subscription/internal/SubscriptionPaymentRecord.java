package com.saumya.pos.subscription.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="subscription_payments")
public class SubscriptionPaymentRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="subscription_invoice_id",nullable=false) private Long invoiceId;
    @Column(nullable=false,length=60) private String provider;
    @Column(name="platform_merchant_reference",nullable=false,length=160) private String platformMerchantReference;
    @Column(name="provider_payment_reference",length=160) private String providerPaymentReference;
    @Column(name="idempotency_key",nullable=false,length=100) private String idempotencyKey;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=24) private String status;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="confirmed_at") private Instant confirmedAt;
    protected SubscriptionPaymentRecord(){}
    public long id(){return id;}public long tenantId(){return tenantId;}public long invoiceId(){return invoiceId;}public String provider(){return provider;}public String platformMerchantReference(){return platformMerchantReference;}public String providerPaymentReference(){return providerPaymentReference;}public BigDecimal amount(){return amount;}public String currency(){return currency;}public String status(){return status;}public Instant createdAt(){return createdAt;}public Instant confirmedAt(){return confirmedAt;}
}
