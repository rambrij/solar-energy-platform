package com.saumya.pos.inventory.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="stock_reservations")
public class StockReservation {
    @Embeddable public static class Key implements Serializable {
        @Column(name="tenant_id") private long tenantId;
        @Column(name="order_id",length=36) private String orderId;
        @Column(name="product_id") private long productId;
        public Key(){} public Key(long tenant,String order,long product){tenantId=tenant;orderId=order;productId=product;}
        @Override public boolean equals(Object o){return o instanceof Key k&&tenantId==k.tenantId&&productId==k.productId&&Objects.equals(orderId,k.orderId);}
        @Override public int hashCode(){return Objects.hash(tenantId,orderId,productId);}
    }
    @EmbeddedId private Key id;
    @Column(name="branch_id",nullable=false) private long branchId;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false,length=16) private String state;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="expires_at",nullable=false) private Instant expiresAt;
    protected StockReservation(){}
    public StockReservation(long tenant,String order,long product,long branch,int qty,Instant expires){id=new Key(tenant,order,product);branchId=branch;quantity=qty;state="HELD";expiresAt=expires;}
    public long product(){return id.productId;} public long branch(){return branchId;} public int quantity(){return quantity;}
    public void release(){state="RELEASED";} public void consume(){state="CONSUMED";}
}
