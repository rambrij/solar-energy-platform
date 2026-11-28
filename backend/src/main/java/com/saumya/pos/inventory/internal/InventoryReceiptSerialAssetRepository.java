package com.saumya.pos.inventory.internal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InventoryReceiptSerialAssetRepository extends JpaRepository<InventoryReceiptSerialAsset,Long>{
 List<InventoryReceiptSerialAsset> findByTenantAndBranchAndReceiptIdOrderByIdAsc(long tenant,long branch,String receiptId);
}
