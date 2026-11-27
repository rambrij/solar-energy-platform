package com.saumya.pos.integration.internal;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage,String>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select m from OutboxMessage m where (m.state='PENDING' and m.availableAt<=:now) or (m.state='PROCESSING' and m.leaseUntil<=:now) order by m.availableAt,m.createdAt")
 List<OutboxMessage> claimable(@Param("now") Instant now,Pageable page);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<OutboxMessage> findByEventIdAndLeaseOwnerAndState(String eventId,String leaseOwner,String state);
}