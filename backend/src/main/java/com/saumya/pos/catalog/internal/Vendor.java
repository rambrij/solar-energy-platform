package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
@Entity @Table(name="tenants") @Immutable
public class Vendor {
    @Id private Long id;
    @Column(length=100) private String slug;
    @Column(nullable=false,length=160) private String name;
    @Column(nullable=false) private boolean active;
    protected Vendor() {}
    public Long id(){return id;} public String slug(){return slug;} public String name(){return name;} public boolean active(){return active;}
}
