package com.saumya.pos.cart.internal;
import jakarta.persistence.*;
@Entity @Table(name="carts")
public class ShoppingCart {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id",nullable=false) private long tenantId;
    @Column(name="customer_id",nullable=false) private long customerId;
    @Column(nullable=false,length=16) private String state;
    @Version @Column(nullable=false) private Long version;
    protected ShoppingCart() {}
    public ShoppingCart(String id,long tenant,long customer){this.id=id;tenantId=tenant;customerId=customer;state="ACTIVE";}
    public String id(){return id;} public long tenantId(){return tenantId;} public long customerId(){return customerId;}
    public void archive(){state="ARCHIVED";} public void activate(){state="ACTIVE";}
}
