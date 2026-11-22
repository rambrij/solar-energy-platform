package com.saumya.pos.identity.internal;
import jakarta.persistence.*;
@Entity @Table(name="identity_sequence")
public class IdentitySequence {
 @Id private Integer id;
 @Column(name="next_id",nullable=false) private long next;
 protected IdentitySequence(){}
 public long allocate(){if(next==Long.MAX_VALUE)throw new IllegalStateException("Identity capacity exhausted");return next++;}
}
