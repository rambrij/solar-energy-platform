package com.saumya.pos.identity.internal;
import jakarta.persistence.*;

@Entity @Table(name="customer_profiles")
public class CustomerProfile {
    @Id private Long id;
    @Column(name="user_id",nullable=false,unique=true) private Long userId;
    @Column(name="display_name",length=120) private String displayName;
    protected CustomerProfile() {}
    public CustomerProfile(long id,long user){this(id,user,null);}
    public CustomerProfile(long id,long user,String displayName){this.id=id;this.userId=user;this.displayName=displayName;}
    public Long id(){return id;}
    public Long userId(){return userId;}
    public String displayName(){return displayName;}
    public void displayName(String value){this.displayName=value;}
}
