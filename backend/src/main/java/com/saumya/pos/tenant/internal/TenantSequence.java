package com.saumya.pos.tenant.internal;

import jakarta.persistence.*;

@Entity
@Table(name = "tenant_sequences")
public class TenantSequence {
    @Id
    private Integer id;

    @Column(name = "next_id", nullable = false)
    private long next;

    protected TenantSequence() {}

    public long allocate() {
        if (next == Long.MAX_VALUE) throw new IllegalStateException("Tenant identity capacity exhausted");
        return next++;
    }
}
