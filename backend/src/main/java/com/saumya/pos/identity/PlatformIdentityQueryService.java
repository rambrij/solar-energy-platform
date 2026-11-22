package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.GlobalUserRepository;
import com.saumya.pos.identity.internal.PlatformGrantRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only application boundary for platform identity administration. */
@Service
public class PlatformIdentityQueryService {
    private final GlobalUserRepository users;
    private final PlatformGrantRepository grants;

    public PlatformIdentityQueryService(GlobalUserRepository users, PlatformGrantRepository grants) {
        this.users = users;
        this.grants = grants;
    }

    public record Identity(String username, boolean mfaEnrolled, int failedMfaAttempts,
                           String lockedUntil, List<String> permissions) {}

    public List<Identity> list() {
        return grants.usernames().stream().map(username -> {
            var user = users.findByUsername(username).orElseThrow();
            return new Identity(username, user.platformMfaEnrolled(), user.platformMfaFailedAttempts(),
                user.platformMfaLockedUntil() == null ? null : user.platformMfaLockedUntil().toString(),
                grants.permissions(username));
        }).toList();
    }
}
