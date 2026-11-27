package com.saumya.pos.catalog.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="solar_packages", uniqueConstraints={
    @UniqueConstraint(name="uk_solar_package_tenant_id",columnNames={"tenant_id","id"}),
    @UniqueConstraint(name="uk_solar_package_tenant_sku",columnNames={"tenant_id","sku"})
})
public class SolarPackage {
    @Id private Long id;
    @Column(name="tenant_id",nullable=false) private long tenantId;
    @Column(nullable=false,length=80) private String sku;
    @Column(nullable=false,length=160) private String name;
    @Column(length=2000) private String description;
    @Column(name="system_capacity_kw",nullable=false,precision=8,scale=3) private BigDecimal systemCapacityKw;
    @Column(name="package_price",nullable=false,precision=19,scale=2) private BigDecimal packagePrice;
    @Column(name="tax_rate",nullable=false,precision=5,scale=2) private BigDecimal taxRate;
    @Column(nullable=false) private boolean published;
    @Version @Column(nullable=false) private long version;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    protected SolarPackage() {}
    public SolarPackage(long id,long tenantId,String sku,String name,String description,
            BigDecimal systemCapacityKw,BigDecimal packagePrice,BigDecimal taxRate,
            boolean published,Instant now) {
        this.id=id;this.tenantId=tenantId;this.sku=sku;this.name=name;this.description=description;
        this.systemCapacityKw=systemCapacityKw;this.packagePrice=packagePrice;this.taxRate=taxRate;
        this.published=published;this.createdAt=now;this.updatedAt=now;
    }
    public void edit(String sku,String name,String description,BigDecimal capacity,BigDecimal price,
            BigDecimal taxRate,boolean published,Instant now) {
        this.sku=sku;this.name=name;this.description=description;this.systemCapacityKw=capacity;
        this.packagePrice=price;this.taxRate=taxRate;this.published=published;this.updatedAt=now;
    }
    public long id(){return id;} public long tenantId(){return tenantId;} public String sku(){return sku;}
    public String name(){return name;} public String description(){return description;}
    public BigDecimal systemCapacityKw(){return systemCapacityKw;} public BigDecimal packagePrice(){return packagePrice;}
    public BigDecimal taxRate(){return taxRate;} public boolean published(){return published;}
    public long version(){return version;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
}
