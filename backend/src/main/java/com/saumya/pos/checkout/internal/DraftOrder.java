package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="orders")
public class DraftOrder {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenantId;
    @Column(name="customer_id") private Long customerId;
    @Column(name="branch_id") private Long branchId;
    @Column(length=100) private String cashier;
    @Column(name="session_id",length=36) private String sessionId;
    @Column(name="sales_channel",nullable=false,length=16) private String channel;
    @Column(name="idempotency_key",nullable=false,length=80) private String key;
    @Column(name="request_hash",nullable=false,length=64) private String hash;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal total;
    @Column(name="cash_received",precision=19,scale=2) private BigDecimal cashReceived;
    @Column(name="change_given",precision=19,scale=2) private BigDecimal changeGiven;
    @Column(name="receipt_number") private Long receiptNumber;
    @Column(name="tax_invoice",nullable=false) private boolean taxInvoice;
    @Column(name="invoice_requested",nullable=false) private boolean invoiceRequested;
    @Column(nullable=false,length=24) private String status;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="expires_at") private Instant expiresAt;
    @Column(name="stock_branch_id") private Long stockBranch;
    @Column(name="draft_owner",length=80) private String draftOwner;
    @Column(name="cancellation_reason",length=250) private String cancellationReason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="status_changed_at") private Instant statusChangedAt;
    protected DraftOrder(){}
    public DraftOrder(String id,long tenant,Long customer,Long branch,String cashier,String session,String channel,String key,String hash,BigDecimal total,long stockBranch,String owner,Instant expires,boolean invoice){
        this.id=id;tenantId=tenant;customerId=customer;branchId=branch;this.cashier=cashier;sessionId=session;this.channel=channel;this.key=key;this.hash=hash;this.total=total;this.stockBranch=stockBranch;draftOwner=owner;expiresAt=expires;invoiceRequested=invoice;status="DRAFT";
    }
    public String id(){return id;} public long tenant(){return tenantId;} public Long customer(){return customerId;} public Long branch(){return branchId;}
    public String cashier(){return cashier;} public String session(){return sessionId;} public String channel(){return channel;} public String hash(){return hash;}
    public BigDecimal total(){return total;} public BigDecimal cash(){return cashReceived;} public String status(){return status;} public Instant expires(){return expiresAt;}
    public boolean invoiceRequested(){return invoiceRequested;}
    public void finish(String state,Instant now){status=state;draftOwner=null;statusChangedAt=now;}
    public void cancel(String reason,Instant now){cancellationReason=reason;finish("CANCELLED",now);}
    public void pay(BigDecimal cash,long receipt,boolean invoice,Instant now){cashReceived=cash;changeGiven=cash.subtract(total);receiptNumber=receipt;taxInvoice=invoice;finish("PAID",now);}
}
