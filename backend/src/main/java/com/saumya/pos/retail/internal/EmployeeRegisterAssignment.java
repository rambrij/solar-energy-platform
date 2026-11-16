package com.saumya.pos.retail.internal;
import jakarta.persistence.*;
@Entity @Table(name="employee_register_assignments")
@IdClass(EmployeeRegisterAssignment.Key.class)
public class EmployeeRegisterAssignment {
    @Id @Column(name="tenant_id") private long tenant;
    @Id @Column(name="username",length=100) private String username;
    @Id @Column(name="register_id") private long register;
    @Column(name="branch_id") private long branch;
    @Column(nullable=false) private boolean active;
    protected EmployeeRegisterAssignment(){}
    public static class Key implements java.io.Serializable {
        public long tenant;public String username;public long register;
        public Key(){}
        @Override public boolean equals(Object o){return o instanceof Key k&&tenant==k.tenant&&register==k.register&&java.util.Objects.equals(username,k.username);}
        @Override public int hashCode(){return java.util.Objects.hash(tenant,username,register);}
    }
}
