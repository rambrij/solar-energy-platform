package com.saumya.pos.identity.internal;

import jakarta.persistence.*;

@Entity @Table(name="users")
public class GlobalUser {
    @Id private Long id;
    @Column(nullable=false,unique=true,length=100) private String username;
    @Column(name="password_hash",nullable=false,length=200) private String password;
    @Column(nullable=false) private boolean enabled;
    protected GlobalUser(){}
    public GlobalUser(long id,String username,String password){this(id,username,password,true);}
    public GlobalUser(long id,String username,String password,boolean enabled){this.id=id;this.username=username;this.password=password;this.enabled=enabled;}
    public long id(){return id;} public String username(){return username;} public String password(){return password;} public boolean enabled(){return enabled;}
    public void activate(String passwordHash){this.password=passwordHash;this.enabled=true;}
}
