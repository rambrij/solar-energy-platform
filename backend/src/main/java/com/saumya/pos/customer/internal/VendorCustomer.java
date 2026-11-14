package com.saumya.pos.customer.internal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="tenant_customers")
public class VendorCustomer {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="customer_id",nullable=false) private long customer;
 @Column(name="consent_at",nullable=false) private Instant consent;
 @Column(nullable=false,length=40) private String provenance;
 protected VendorCustomer(){}
 public VendorCustomer(long tenant,long customer,Instant consent){id=java.util.UUID.randomUUID().toString();this.tenant=tenant;this.customer=customer;this.consent=consent;provenance="CUSTOMER_AUTHENTICATED_POS_CONSENT";}
}
