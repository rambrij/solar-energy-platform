package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
@Entity @Table(name="catalog_sequences") public class CatalogSequence {
 @Id @Column(length=30) private String name;
 @Column(name="next_id",nullable=false) private long next;
 protected CatalogSequence(){}public long allocate(){if(next==Long.MAX_VALUE)throw new IllegalStateException("Catalogue identity capacity exhausted");return next++;}
}
