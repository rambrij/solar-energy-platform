package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
/** Immutable read model of the invoice issued in the sale transaction. */
@Entity @Table(name="vendor_invoices")
@org.hibernate.annotations.Immutable
public class InvoiceSnapshot {
 @Embeddable public static class Key implements Serializable {
  @Column(name="tenant_id") private long tenant;
  @Column(name="order_id",length=36) private String order;
  protected Key(){} public Key(long tenant,String order){this.tenant=tenant;this.order=order;}
  public boolean equals(Object o){return o instanceof Key k&&tenant==k.tenant&&Objects.equals(order,k.order);}
  public int hashCode(){return Objects.hash(tenant,order);}
 }
 @EmbeddedId private Key id;
 @Column(name="invoice_number",length=100) private String number;
 @Column(name="seller_name",length=160) private String seller;
 @Column(name="outlet_name",length=160) private String outlet;
 @Column(precision=19,scale=2) private BigDecimal total;
 protected InvoiceSnapshot(){}
 public String number(){return number;} public String seller(){return seller;} public String outlet(){return outlet;} public BigDecimal total(){return total;}
}
