package com.saumya.pos.checkout.internal;
import jakarta.persistence.*;
@Entity @Table(name="branches") @org.hibernate.annotations.Immutable
public class BranchReturnPolicy {
 @Id private Long id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="pos_return_window_days",nullable=false) private int days;
 @Column(name="cash_refunds_enabled",nullable=false) private boolean cashRefunds;
 public boolean cashRefunds(){return cashRefunds;}
 protected BranchReturnPolicy(){}
 public int days(){return days;}
}
