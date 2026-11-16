package com.saumya.pos.subscription.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="subscription_plans")
public class SubscriptionPlan {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="plan_key",nullable=false,unique=true,length=80) private String planKey;
    @Column(name="display_name",nullable=false,length=120) private String displayName;
    @Column(nullable=false) private boolean active;
    @Column(name="current_version",nullable=false) private int currentVersion;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected SubscriptionPlan(){}
    public SubscriptionPlan(String key,String name,Instant now){this.planKey=key;this.displayName=name;this.active=true;this.currentVersion=0;this.createdAt=now;this.updatedAt=now;}
    public long id(){return id;}public String planKey(){return planKey;}public String displayName(){return displayName;}public boolean active(){return active;}public int currentVersion(){return currentVersion;}public Instant createdAt(){return createdAt;}public Instant updatedAt(){return updatedAt;}
    public void currentVersion(int version,String name,Instant now){if(version<=currentVersion)throw new IllegalArgumentException("Plan versions must increase");this.currentVersion=version;this.displayName=name;this.updatedAt=now;}
    public void active(boolean value,Instant now){this.active=value;this.updatedAt=now;}
}
