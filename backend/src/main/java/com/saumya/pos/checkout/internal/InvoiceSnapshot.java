package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
/** Immutable issued invoice owned by checkout. */
@Entity @Table(name="vendor_invoices")
public class InvoiceSnapshot {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenant;
  @Column(name="order_id",length=36) private String order;
  protected Key(){} public Key(long tenant,String order){this.tenant=tenant;this.order=order;}
  public boolean equals(Object o){return o instanceof Key k&&tenant==k.tenant&&Objects.equals(order,k.order);}
  public int hashCode(){return Objects.hash(tenant,order);}
 }
 @EmbeddedId private Key id;
 @Column(name="invoice_number",length=100,updatable=false) private String number;
 @Column(name="seller_name",length=160,updatable=false) private String seller;
 @Column(name="outlet_name",length=160,updatable=false) private String outlet;
 @Column(precision=19,scale=2,updatable=false) private BigDecimal total;
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP) @Column(name="issued_at",insertable=false,updatable=false) private java.time.Instant issuedAt;
 public java.time.Instant issuedAt(){return issuedAt;}
 protected InvoiceSnapshot(){}
 public InvoiceSnapshot(long tenant,String order,String number,String seller,String outlet,BigDecimal total){id=new Key(tenant,order);this.number=number;this.seller=seller;this.outlet=outlet;this.total=total;}
 public String number(){return number;} public String seller(){return seller;} public String outlet(){return outlet;} public BigDecimal total(){return total;}
}
