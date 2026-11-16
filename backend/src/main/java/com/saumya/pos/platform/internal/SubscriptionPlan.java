package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="subscription_plans")
public class SubscriptionPlan {
    @Id @Column(length=40) private String code;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false) private boolean active;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant createdAt;
    protected SubscriptionPlan(){}
    public SubscriptionPlan(String code,String name,Instant now){this.code=code;this.name=name;active=true;createdAt=now.truncatedTo(ChronoUnit.MICROS);}
    public String code(){return code;} public String name(){return name;} public boolean active(){return active;} public Instant createdAt(){return createdAt;}
    public void rename(String name){this.name=name;} public void active(boolean value){active=value;}
}
