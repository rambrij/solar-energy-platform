package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** SaaS subscription invoice. Lives in its own table, never in vendor orders/invoices (SUB-010). */
@Entity @Table(name="subscription_invoices")
public class SubscriptionInvoice {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenant;
    @Column(name="subscription_id",nullable=false,length=36) private String subscriptionId;
    @Column(name="invoice_number",nullable=false,length=40) private String number;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="period_start",nullable=false) private Instant periodStart;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="period_end",nullable=false) private Instant periodEnd;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal tax;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal total;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=8) private String status;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="issued_at",nullable=false) private Instant issuedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="due_at",nullable=false) private Instant dueAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="paid_at") private Instant paidAt;
    protected SubscriptionInvoice(){}
    public SubscriptionInvoice(String id,long tenant,String subscriptionId,String number,Instant periodStart,Instant periodEnd,BigDecimal amount,BigDecimal tax,BigDecimal total,String currency,Instant issued,Instant due){
        this.id=id;this.tenant=tenant;this.subscriptionId=subscriptionId;this.number=number;this.periodStart=periodStart.truncatedTo(ChronoUnit.MICROS);this.periodEnd=periodEnd.truncatedTo(ChronoUnit.MICROS);this.amount=amount;this.tax=tax;this.total=total;this.currency=currency;status="OPEN";issuedAt=issued.truncatedTo(ChronoUnit.MICROS);dueAt=due.truncatedTo(ChronoUnit.MICROS);
    }
    public String id(){return id;} public long tenant(){return tenant;} public String subscriptionId(){return subscriptionId;} public String number(){return number;} public Instant periodStart(){return periodStart;} public Instant periodEnd(){return periodEnd;}
    public BigDecimal amount(){return amount;} public BigDecimal tax(){return tax;} public BigDecimal total(){return total;} public String currency(){return currency;} public String status(){return status;} public Instant issuedAt(){return issuedAt;} public Instant dueAt(){return dueAt;} public Instant paidAt(){return paidAt;}
    public void paid(Instant now){status="PAID";paidAt=now.truncatedTo(ChronoUnit.MICROS);}
}
