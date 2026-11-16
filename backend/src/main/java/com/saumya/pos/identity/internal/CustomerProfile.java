package com.saumya.pos.identity.internal;
import jakarta.persistence.*;
@Entity @Table(name="customer_profiles")
public class CustomerProfile {
    @Id private Long id;
    @Column(name="user_id",nullable=false,unique=true) private Long userId;
    protected CustomerProfile() {}
    public CustomerProfile(long id,long user){this.id=id;this.userId=user;}
    public Long id(){return id;}
}
