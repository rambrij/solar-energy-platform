package com.saumya.pos.retail.internal;
import jakarta.persistence.*;
@Entity @Table(name="branches")
public class BranchConfiguration {
 @Id private Long id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(nullable=false,length=160) private String name;
 @Column(name="invoice_required",nullable=false) private boolean invoice;
 @Column(name="manual_tender_enabled",nullable=false) private boolean terminal;
 @Column(name="upi_merchant_reference",length=80) private String upi;
 @Column(name="card_merchant_reference",length=80) private String card;
 @Column(name="split_tender_enabled",nullable=false) private boolean split;
 @Column(name="pos_return_window_days",nullable=false) private int returnDays;
 @Column(name="cash_refunds_enabled",nullable=false) private boolean cashRefunds;
 @Column(name="buyer_details_required",nullable=false) private boolean buyerDetails;
 @Column(name="credit_notes_enabled",nullable=false) private boolean creditNotes;
 @Column(nullable=false,length=3) private String currency;
 @Version @Column(name="settings_version",nullable=false) private long version;
 protected BranchConfiguration(){}
 public BranchConfiguration(long id,long tenant,String name){this.id=id;this.tenant=tenant;this.name=name;currency="INR";}
 public long id(){return id;}public long tenant(){return tenant;}public String name(){return name;}public boolean invoice(){return invoice;}public boolean terminal(){return terminal;}public String upi(){return upi;}public String card(){return card;}public boolean split(){return split;}public int returnDays(){return returnDays;}public boolean cashRefunds(){return cashRefunds;}public boolean buyerDetails(){return buyerDetails;}public boolean creditNotes(){return creditNotes;}public String currency(){return currency;}public long version(){return version;}
 public void update(String name,boolean invoice,boolean terminal,String upi,String card,boolean split,int returnDays,boolean cashRefunds,boolean buyerDetails,boolean creditNotes,String currency){this.name=name;this.invoice=invoice;this.terminal=terminal;this.upi=upi;this.card=card;this.split=split;this.returnDays=returnDays;this.cashRefunds=cashRefunds;this.buyerDetails=buyerDetails;this.creditNotes=creditNotes;this.currency=currency;}
}
