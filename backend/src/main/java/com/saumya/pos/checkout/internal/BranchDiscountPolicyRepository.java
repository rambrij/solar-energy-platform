package com.saumya.pos.checkout.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchDiscountPolicyRepository extends JpaRepository<BranchDiscountPolicy,Long> {
    Optional<BranchDiscountPolicy> findByTenantAndId(long tenant,long id);
}
