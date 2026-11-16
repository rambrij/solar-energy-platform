package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A global platform setting. For secret keys the value is only a reference (env:/vault:), never the secret. */
@Entity @Table(name="platform_configuration")
public class ConfigEntry {
    @Id @Column(name="config_key",length=80) private String key;
    @Column(name="config_value",nullable=false,length=250) private String value;
    @Column(nullable=false) private int version;
    @Column(name="updated_by",nullable=false,length=100) private String updatedBy;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected ConfigEntry(){}
    public ConfigEntry(String key,String value,String actor,Instant now){this.key=key;this.value=value;version=1;updatedBy=actor;updatedAt=now.truncatedTo(ChronoUnit.MICROS);}
    public String key(){return key;} public String value(){return value;} public int version(){return version;} public String updatedBy(){return updatedBy;} public Instant updatedAt(){return updatedAt;}
    public void change(String value,String actor,Instant now){this.value=value;version++;updatedBy=actor;updatedAt=now.truncatedTo(ChronoUnit.MICROS);}
}
