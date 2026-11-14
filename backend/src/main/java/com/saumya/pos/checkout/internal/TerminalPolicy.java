package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
@Entity @Table(name="branches") @org.hibernate.annotations.Immutable
public class TerminalPolicy {
 @Id private Long id;
 @Column(name="tenant_id") private long tenant;
 @Column(name="manual_tender_enabled") private boolean enabled;
 @Column(name="upi_merchant_reference",length=80) private String upi;
 @Column(name="card_merchant_reference",length=80) private String card;
 @Column(name="split_tender_enabled") private boolean split;
 public boolean split(){return split;}
 protected TerminalPolicy(){}
 public boolean enabled(){return enabled;}
 public String merchant(String method){return "UPI".equals(method)?upi:card;}
}
