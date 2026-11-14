package com.saumya.pos.checkout.internal;
import java.util.Optional;
import org.springframework.data.repository.Repository;
public interface TerminalPolicyRepository extends Repository<TerminalPolicy,Long>{Optional<TerminalPolicy> findByTenantAndId(long tenant,long id);}
