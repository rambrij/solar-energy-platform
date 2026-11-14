package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
@Entity @Table(name="order_items")
public class DraftLine {
    @Embeddable public static class Key implements Serializable {
        @Column(name="tenant_id") private long tenant;
        @Column(name="order_id",length=36) private String order;
        @Column(name="product_id") private long product;
        public Key(){} public Key(long tenant,String order,long product){this.tenant=tenant;this.order=order;this.product=product;}
        @Override public boolean equals(Object o){return o instanceof Key k&&tenant==k.tenant&&product==k.product&&Objects.equals(order,k.order);}
        @Override public int hashCode(){return Objects.hash(tenant,order,product);}
    }
    @EmbeddedId private Key id;
    @Column(nullable=false,length=160) private String name;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal price;
    @Column(name="tax_rate",nullable=false,precision=5,scale=2) private BigDecimal taxRate;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal tax;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal total;
    @Column(name="list_price",nullable=false,precision=19,scale=2) private BigDecimal listPrice;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal discount;
    @Column(name="discount_percent",nullable=false,precision=5,scale=2) private BigDecimal discountPercent;
    protected DraftLine(){}
    public DraftLine(long tenant,String order,long product,String name,int quantity,BigDecimal price,BigDecimal rate,BigDecimal tax,BigDecimal total){id=new Key(tenant,order,product);this.name=name;this.quantity=quantity;this.price=price;taxRate=rate;this.tax=tax;this.total=total;listPrice=price;discount=BigDecimal.ZERO;discountPercent=BigDecimal.ZERO;}
    public DraftLine(long tenant,String order,long product,String name,int quantity,BigDecimal price,BigDecimal rate,BigDecimal tax,BigDecimal total,BigDecimal listPrice,BigDecimal percent){this(tenant,order,product,name,quantity,price,rate,tax,total);this.listPrice=listPrice;discountPercent=percent;discount=listPrice.subtract(price).multiply(BigDecimal.valueOf(quantity));}
    public BigDecimal listPrice(){return listPrice;} public BigDecimal discount(){return discount;} public BigDecimal discountPercent(){return discountPercent;}
    public long product(){return id.product;} public String name(){return name;} public int quantity(){return quantity;}
    public BigDecimal price(){return price;} public BigDecimal rate(){return taxRate;} public BigDecimal tax(){return tax;} public BigDecimal total(){return total;}
}
