package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
@Entity @Table(name="tenants") @Immutable
public class Vendor {
    @Id private Long id;
    @Column(length=100) private String slug;
    @Column(nullable=false,length=160) private String name;
    @Column(nullable=false,length=16) private String status;
    @Column(name="online_branch_id") private Long onlineBranchId;
    protected Vendor() {}
    public Long id(){return id;} public String slug(){return slug;} public String name(){return name;} public boolean active(){return "ACTIVE".equals(status);}
    public Long onlineBranchId(){return onlineBranchId;}
}
