package com.saumya.pos.returns.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
@Entity @Table(name="pos_cash_refund_items") @org.hibernate.annotations.Immutable
public class CashRefundItem {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenant;
  @Column(name="refund_id",length=36) private String refund;
  @Column(name="product_id") private long product;
  public Key(){} public Key(long tenant,String refund,long product){this.tenant=tenant;this.refund=refund;this.product=product;}
  @Override public boolean equals(Object o){return o instanceof Key k&&tenant==k.tenant&&product==k.product&&Objects.equals(refund,k.refund);}
  @Override public int hashCode(){return Objects.hash(tenant,refund,product);}
 }
 @EmbeddedId private Key id;
 @Column(name="return_id",nullable=false,length=36) private String returnId;
 @Column(name="order_id",nullable=false,length=36) private String orderId;
 @Column(nullable=false,length=160) private String name;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal net;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal tax;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
 protected CashRefundItem(){}
 public CashRefundItem(long tenant,String id,String returned,String order,long product,String name,int quantity,BigDecimal net,BigDecimal tax,BigDecimal amount){this.id=new Key(tenant,id,product);returnId=returned;orderId=order;this.name=name;this.quantity=quantity;this.net=net;this.tax=tax;this.amount=amount;}
 public long product(){return id.product;} public int quantity(){return quantity;} public String name(){return name;} public BigDecimal net(){return net;} public BigDecimal tax(){return tax;} public BigDecimal amount(){return amount;}
}
