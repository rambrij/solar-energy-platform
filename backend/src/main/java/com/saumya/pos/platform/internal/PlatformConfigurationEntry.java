package com.saumya.pos.platform.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="platform_configuration")
public class PlatformConfigurationEntry {
    @Id @Column(name="config_key",length=100) private String key;
    @Column(nullable=false,length=60) private String category;
    @Column(name="plain_value",columnDefinition="TEXT") private String plainValue;
    @Column(name="secret_reference",length=255) private String secretReference;
    @Column(name="is_sensitive",nullable=false) private boolean sensitive;
    @Column(name="updated_by",nullable=false,length=100) private String updatedBy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected PlatformConfigurationEntry() {}
    public PlatformConfigurationEntry(String key,String category,String value,String secretReference,boolean sensitive,String actor,Instant now){
        this.key=key;this.category=category;this.plainValue=value;this.secretReference=secretReference;this.sensitive=sensitive;this.updatedBy=actor;this.updatedAt=now;
    }
    public String key(){return key;}public String category(){return category;}public String plainValue(){return plainValue;}public String secretReference(){return secretReference;}public boolean sensitive(){return sensitive;}public String updatedBy(){return updatedBy;}public Instant updatedAt(){return updatedAt;}public long version(){return version;}
    public void update(String category,String value,String ref,boolean sensitive,String actor,Instant now){this.category=category;this.plainValue=value;this.secretReference=ref;this.sensitive=sensitive;this.updatedBy=actor;this.updatedAt=now;}
}
