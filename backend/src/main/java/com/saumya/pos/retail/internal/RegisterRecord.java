package com.saumya.pos.retail.internal;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
@Entity @Table(name="registers")
public class RegisterRecord {
 @Id private long id;
 @Column(name="tenant_id") private long tenant;
 @Column(name="branch_id") private long branch;
 protected RegisterRecord(){}
 public RegisterRecord(long id,long tenant,long branch){this.id=id;this.tenant=tenant;this.branch=branch;}
}
