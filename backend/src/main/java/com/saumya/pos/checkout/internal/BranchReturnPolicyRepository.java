package com.saumya.pos.checkout.internal;
import java.util.Optional;
import org.springframework.data.repository.Repository;
public interface BranchReturnPolicyRepository extends Repository<BranchReturnPolicy,Long>{Optional<BranchReturnPolicy> findByTenantAndId(long tenant,long id);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from BranchReturnPolicy p where p.tenant=:tenant and p.id=:id") Optional<BranchReturnPolicy> locked(long tenant,long id);}
