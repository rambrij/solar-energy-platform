package com.saumya.pos.checkout.internal;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface DraftLineRepository extends JpaRepository<DraftLine,DraftLine.Key> {
    @Query("select l from DraftLine l where l.id.tenant=:tenant and l.id.order=:order order by l.id.product")
    List<DraftLine> lines(long tenant,String order);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select l from DraftLine l where l.id.tenant=:tenant and l.id.order=:order order by l.id.product")
    List<DraftLine> lockedLines(long tenant,String order);
}
