package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
@Entity @Table(name="catalog_categories")
public class Category {
 @Id private Long id;
 @Column(name="tenant_id",nullable=false) private long tenantId;
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false,length=100) private String slug;
 @Column(nullable=false) private boolean active=true;
 @Version @Column(nullable=false) private long version;
 protected Category(){}
 public Category(long id,long tenant,String name,String slug){this.id=id;tenantId=tenant;this.name=name;this.slug=slug;}
 public long id(){return id;}public String name(){return name;}public String slug(){return slug;}public boolean active(){return active;}public long version(){return version;}
 public void edit(String name,String slug,boolean active){this.name=name;this.slug=slug;this.active=active;}

}
