package com.saumya.pos.identity.internal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface PlatformGrantRepository extends JpaRepository<PlatformGrant,PlatformGrant.Key> {
    @Query("select g.permission from PlatformGrant g where g.username=:username order by g.permission")
    List<String> permissions(String username);
}
