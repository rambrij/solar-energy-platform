package com.saumya.pos.membership.internal;
import jakarta.persistence.*;
@Entity @Table(name="employees")
public class EmployeeMembership {
 @Id @Column(length=100) private String username;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(nullable=false) private boolean enabled;
 protected EmployeeMembership(){}
 public EmployeeMembership(String username,long tenant,long branch,boolean enabled){this.username=username;this.tenant=tenant;this.branch=branch;this.enabled=enabled;}
 public String username(){return username;}public long tenant(){return tenant;}public long branch(){return branch;}public boolean enabled(){return enabled;}
}
