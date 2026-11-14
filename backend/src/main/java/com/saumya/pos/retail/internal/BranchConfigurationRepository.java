package com.saumya.pos.retail.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface BranchConfigurationRepository extends JpaRepository<BranchConfiguration,Long>{Optional<BranchConfiguration> findByTenantAndId(long tenant,long id);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select b from BranchConfiguration b where b.tenant=:tenant and b.id=:id") Optional<BranchConfiguration> locked(long tenant,long id);
}
