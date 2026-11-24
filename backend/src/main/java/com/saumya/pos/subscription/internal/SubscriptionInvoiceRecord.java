package com.saumya.pos.subscription.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="subscription_invoices")
public class SubscriptionInvoiceRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="subscription_id",nullable=false) private Long subscriptionId;
    @Column(name="invoice_number",nullable=false) private String invoiceNumber;
    @Column(name="billing_period_start",nullable=false) private LocalDate periodStart;
    @Column(name="billing_period_end",nullable=false) private LocalDate periodEnd;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="plan_snapshot",nullable=false,columnDefinition="json") private String planSnapshot;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal subtotal;
    @Column(name="tax_amount",nullable=false,precision=19,scale=4) private BigDecimal taxAmount;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal total;
    @Column(nullable=false,length=20) private String status;
    @Column(name="due_at",nullable=false) private Instant dueAt;
    @Column(name="issued_at",nullable=false) private Instant issuedAt;
    @Column(name="mock_next_attempt_at") private Instant mockNextAttemptAt;
    protected SubscriptionInvoiceRecord(){}
    public SubscriptionInvoiceRecord(long tenantId,long subscriptionId,String number,LocalDate start,LocalDate end,
                                     String currency,String snapshot,BigDecimal amount,Instant now){
        this.tenantId=tenantId;this.subscriptionId=subscriptionId;this.invoiceNumber=number;
        this.periodStart=start;this.periodEnd=end;this.currency=currency;this.planSnapshot=snapshot;
        this.subtotal=amount;this.taxAmount=BigDecimal.ZERO;this.total=amount;this.status="OPEN";
        this.dueAt=now;this.issuedAt=now;this.mockNextAttemptAt=now;
    }
    public void scheduleMockRetry(Instant next){mockNextAttemptAt=next;}
    public void markMockPaid(){status="PAID";mockNextAttemptAt=null;}
    public Instant mockNextAttemptAt(){return mockNextAttemptAt;}
    public long id(){return id;}public long tenantId(){return tenantId;}public long subscriptionId(){return subscriptionId;}public String invoiceNumber(){return invoiceNumber;}public LocalDate periodStart(){return periodStart;}public LocalDate periodEnd(){return periodEnd;}public String currency(){return currency;}public BigDecimal subtotal(){return subtotal;}public BigDecimal taxAmount(){return taxAmount;}public BigDecimal total(){return total;}public String status(){return status;}public Instant dueAt(){return dueAt;}public Instant issuedAt(){return issuedAt;}
}
