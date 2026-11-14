package com.saumya.pos.checkout.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One settled tender of a split-tender sale. Immutable once written. */
@Entity @Table(name="order_tenders")
public class OrderTender {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenant;
    @Column(name="order_id",nullable=false,length=36) private String orderRef;
    @Column(name="tender_no",nullable=false) private int number;
    @Column(nullable=false,length=8) private String method;
    @Column(name="applied_amount",nullable=false,precision=19,scale=2) private BigDecimal applied;
    @Column(name="tendered_amount",nullable=false,precision=19,scale=2) private BigDecimal tendered;
    @Column(name="change_given",nullable=false,precision=19,scale=2) private BigDecimal change;
    @Column(name="merchant_account",length=80) private String merchant;
    @Column(name="provider_reference",length=80) private String reference;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
    protected OrderTender(){}
    public OrderTender(String id,long tenant,String order,int number,String method,BigDecimal applied,BigDecimal tendered,BigDecimal change,String merchant,String reference,Instant now){
        this.id=id;this.tenant=tenant;orderRef=order;this.number=number;this.method=method;this.applied=applied;this.tendered=tendered;this.change=change;this.merchant=merchant;this.reference=reference;created=now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public String id(){return id;} public int number(){return number;} public String method(){return method;}
    public BigDecimal applied(){return applied;} public BigDecimal tendered(){return tendered;} public BigDecimal change(){return change;}
}
