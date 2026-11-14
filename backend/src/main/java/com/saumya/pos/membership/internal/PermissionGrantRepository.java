package com.saumya.pos.membership.internal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PermissionGrantRepository extends JpaRepository<PermissionGrant,PermissionGrant.Key>{List<PermissionGrant> findByIdUsernameAndTenant(String username,long tenant);}
