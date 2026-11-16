package com.saumya.pos.retail.internal;
import jakarta.persistence.*;
@Entity @Table(name="retail_sequences")
public class RetailSequence {
    @Id @Column(length=30) private String name;
    @Column(name="next_id",nullable=false) private long next;
    protected RetailSequence(){}
    public long allocate(){if(next==Long.MAX_VALUE)throw new IllegalStateException("Retail identity capacity exhausted");return next++;}
}
