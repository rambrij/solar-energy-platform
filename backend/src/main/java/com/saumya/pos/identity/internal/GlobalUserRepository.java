package com.saumya.pos.identity.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GlobalUserRepository extends JpaRepository<GlobalUser,Long>{Optional<GlobalUser> findByUsername(String username);}
