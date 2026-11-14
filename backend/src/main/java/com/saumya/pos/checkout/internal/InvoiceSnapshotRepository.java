package com.saumya.pos.checkout.internal;
import org.springframework.data.repository.Repository;
import java.util.Optional;
public interface InvoiceSnapshotRepository extends Repository<InvoiceSnapshot,InvoiceSnapshot.Key> {
 Optional<InvoiceSnapshot> findById(InvoiceSnapshot.Key id);
}
