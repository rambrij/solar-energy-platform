package com.saumya.pos.inventory.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="inventory")
public class InventoryBalance {
    @Embeddable public static class Key implements Serializable {
        @Column(name="tenant_id") private Long tenantId;
        @Column(name="branch_id") private Long branchId;
        @Column(name="product_id") private Long productId;
        public Key() {}
        @Override public boolean equals(Object o){return o instanceof Key k&&Objects.equals(tenantId,k.tenantId)&&Objects.equals(branchId,k.branchId)&&Objects.equals(productId,k.productId);}
        @Override public int hashCode(){return Objects.hash(tenantId,branchId,productId);}
    }
    @EmbeddedId private Key id;
    @Column(nullable=false) private int quantity;
    protected InventoryBalance() {}
    public void issue(int amount){if(amount<=0||amount>quantity)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Insufficient stock");quantity-=amount;}
}
