package com.saumya.pos.checkout.internal;
public interface BuyerSnapshotRepository extends org.springframework.data.jpa.repository.JpaRepository<BuyerSnapshot,BuyerSnapshot.Key>{
 @Override @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) java.util.Optional<BuyerSnapshot> findById(BuyerSnapshot.Key key);
}
