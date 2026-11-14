package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="products")
public class Product {
    @Id private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(nullable=false,length=80) private String sku;
    @Column(length=80) private String barcode;
    @Column(nullable=false,length=160) private String name;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal price;
    @Column(name="tax_rate",nullable=false,precision=5,scale=2) private BigDecimal taxRate;
    @Column(nullable=false) private boolean published;
    protected Product() {}
    public Long id(){return id;} public String sku(){return sku;} public String barcode(){return barcode;} public String name(){return name;}
    public BigDecimal price(){return price;} public BigDecimal taxRate(){return taxRate;} public boolean published(){return published;}
}
