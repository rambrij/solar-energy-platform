package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="pos_customer_challenges")
public class CustomerChallenge {
 @Id @Column(name="token_hash",length=64) private String hash;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="order_id",nullable=false,length=36) private String order;
 @Column(name="expires_at",nullable=false) private Instant expires;
 @Column(name="approved_customer_id") private Long customer;
 protected CustomerChallenge(){}
 public CustomerChallenge(String hash,long tenant,String order,Instant expires){this.hash=hash;this.tenant=tenant;this.order=order;this.expires=expires;}
 public long tenant(){return tenant;}public String order(){return order;}public Instant expires(){return expires;}public Long customer(){return customer;}public void approve(long customer){this.customer=customer;}
}
