package com.saumya.pos.catalog.internal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="catalog_change_events") public class CatalogChangeEvent {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(nullable=false,length=100) private String actor;
 @Column(nullable=false,length=40) private String action;
 @Column(name="resource_id",nullable=false) private long resource;
 @Column(nullable=false,length=250) private String reason;
 @Column(name="before_state",nullable=false,columnDefinition="TEXT") private String before;
 @Column(name="after_state",nullable=false,columnDefinition="TEXT") private String after;
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP) @Column(name="created_at",nullable=false) private Instant created;
 protected CatalogChangeEvent(){}public CatalogChangeEvent(long tenant,String actor,String action,long resource,String reason,String before,String after,Instant created){id=java.util.UUID.randomUUID().toString();this.tenant=tenant;this.actor=actor;this.action=action;this.resource=resource;this.reason=reason;this.before=before;this.after=after;this.created=created;}
}
