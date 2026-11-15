package com.saumya.pos.inventory.internal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="inventory_receipts")
public class StockReceipt {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(name="product_id",nullable=false) private long product;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,length=100) private String actor;
 @Column(nullable=false,length=250) private String reason;
 @Column(name="command_key",nullable=false,length=80) private String key;
 @Column(name="available_after",nullable=false) private int available;
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 protected StockReceipt(){}
 public StockReceipt(long tenant,long branch,long product,int quantity,String actor,String reason,String key,int available,Instant created){id=java.util.UUID.randomUUID().toString();this.tenant=tenant;this.branch=branch;this.product=product;this.quantity=quantity;this.actor=actor;this.reason=reason;this.key=key;this.available=available;this.created=created;}
 public String id(){return id;}public long product(){return product;}public int quantity(){return quantity;}public String actor(){return actor;}public String reason(){return reason;}public int available(){return available;}public Instant created(){return created;}
}
