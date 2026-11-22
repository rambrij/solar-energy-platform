package com.saumya.pos.retail.internal;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Immutable @Table(name="pos_sessions")
public class ClosedShift {
 @Id @Column(length=36) private String id;
 @Column(name="tenant_id") private long tenant;
 @Column(name="branch_id") private long branch;
 @Column(length=100) private String cashier;
 @Column(name="register_id") private long register;
 @Column(length=16) private String state;
 @Column(name="opening_float",precision=19,scale=2) private BigDecimal opening;
 @Column(name="expected_cash",precision=19,scale=2) private BigDecimal expected;
 @Column(name="counted_cash",precision=19,scale=2) private BigDecimal counted;
 @Column(name="cash_variance",precision=19,scale=2) private BigDecimal variance;
 @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="closed_at") private Instant closed;
 protected ClosedShift(){}
}
