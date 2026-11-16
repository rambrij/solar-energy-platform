package com.saumya.pos.checkout.internal;
import org.springframework.data.repository.Repository;
import java.util.Optional;
public interface InvoiceSnapshotRepository extends org.springframework.data.jpa.repository.JpaRepository<InvoiceSnapshot,InvoiceSnapshot.Key> {
 @Override @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 Optional<InvoiceSnapshot> findById(InvoiceSnapshot.Key id);
}
