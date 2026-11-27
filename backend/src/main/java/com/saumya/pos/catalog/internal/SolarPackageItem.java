package com.saumya.pos.catalog.internal;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name="solar_package_items")
public class SolarPackageItem {
    @Embeddable
    public static class Key implements Serializable {
        @Column(name="tenant_id") private long tenantId;
        @Column(name="package_id") private long packageId;
        @Column(name="product_id") private long productId;
        public Key() {}
        public Key(long tenant,long packageId,long productId){this.tenantId=tenant;this.packageId=packageId;this.productId=productId;}
        public long tenantId(){return tenantId;} public long packageId(){return packageId;} public long productId(){return productId;}
        @Override public boolean equals(Object other){return other instanceof Key k&&tenantId==k.tenantId&&packageId==k.packageId&&productId==k.productId;}
        @Override public int hashCode(){return Objects.hash(tenantId,packageId,productId);}
    }
    @EmbeddedId private Key id;
    @Column(nullable=false) private int quantity;
    @Column(name="product_sku_snapshot",nullable=false,length=80) private String productSkuSnapshot;
    @Column(name="product_name_snapshot",nullable=false,length=160) private String productNameSnapshot;
    @Column(name="unit_price_snapshot",nullable=false,precision=19,scale=2) private BigDecimal unitPriceSnapshot;
    @Column(name="tax_rate_snapshot",nullable=false,precision=5,scale=2) private BigDecimal taxRateSnapshot;
    protected SolarPackageItem() {}
    public SolarPackageItem(long tenant,long packageId,long productId,int quantity,String sku,String name,
            BigDecimal unitPrice,BigDecimal taxRate) {
        this.id=new Key(tenant,packageId,productId);this.quantity=quantity;this.productSkuSnapshot=sku;
        this.productNameSnapshot=name;this.unitPriceSnapshot=unitPrice;this.taxRateSnapshot=taxRate;
    }
    public Key id(){return id;} public int quantity(){return quantity;} public String productSkuSnapshot(){return productSkuSnapshot;}
    public String productNameSnapshot(){return productNameSnapshot;} public BigDecimal unitPriceSnapshot(){return unitPriceSnapshot;}
    public BigDecimal taxRateSnapshot(){return taxRateSnapshot;}
}
