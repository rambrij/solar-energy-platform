package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
@Entity @Table(name="catalog_categories")
public class Category {
 @Id private Long id;
 @Column(name="tenant_id",nullable=false) private long tenantId;
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false,length=100) private String slug;
 protected Category(){}
}
