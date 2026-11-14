package com.saumya.pos.membership.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="employee_permissions")
public class PermissionGrant {
 @Embeddable public static class Key implements Serializable {
  @Column(length=100) private String username;
  @Column(length=80) private String permission;
  protected Key(){}
  Key(String username,String permission){this.username=username;this.permission=permission;}
  public boolean equals(Object other){return other instanceof Key k&&Objects.equals(username,k.username)&&Objects.equals(permission,k.permission);}public int hashCode(){return Objects.hash(username,permission);}
 }
 @EmbeddedId private Key id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 protected PermissionGrant(){}
 public PermissionGrant(long tenant,String username,String permission){this.tenant=tenant;id=new Key(username,permission);}
 public String permission(){return id.permission;}
}
