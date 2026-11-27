package com.saumya.pos.integration.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InboxMessageRepository extends JpaRepository<InboxMessage,Long>{
 Optional<InboxMessage> findByConsumerKeyAndProviderEventId(String consumerKey,String providerEventId);
}