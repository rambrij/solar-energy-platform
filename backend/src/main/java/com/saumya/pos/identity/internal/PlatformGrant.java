package com.saumya.pos.identity.internal;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity @Table(name="platform_user_permissions") @IdClass(PlatformGrant.Key.class)
public class PlatformGrant {
    @Id @Column(length=100) private String username;
    @Id @Column(length=60) private String permission;
    protected PlatformGrant() {}
    public String permission(){return permission;}
    public static class Key implements Serializable {
        public String username;
        public String permission;
        public Key() {}
        @Override public boolean equals(Object other){return other instanceof Key k && Objects.equals(username,k.username) && Objects.equals(permission,k.permission);}
        @Override public int hashCode(){return Objects.hash(username,permission);}
    }
}
