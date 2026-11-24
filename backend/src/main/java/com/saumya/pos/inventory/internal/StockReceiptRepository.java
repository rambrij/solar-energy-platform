package com.saumya.pos.inventory.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface StockReceiptRepository extends JpaRepository<StockReceipt,String>{
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 Optional<StockReceipt> findByTenantAndBranchAndKey(long tenant,long branch,String key);
 Page<StockReceipt> findByTenantAndBranchOrderByCreatedDescIdDesc(long tenant,long branch,Pageable page);
 Page<StockReceipt> findByTenantAndBranchAndProductOrderByCreatedDescIdDesc(long tenant,long branch,long product,Pageable page);
}
