package com.saumya.pos.platform;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Platform authorization adapter that depends only on Spring Security, not vendor membership services. */
public final class PlatformAuthorization {
    private PlatformAuthorization() {}

    public static Authentication require(String permission, String expectedActor) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toUnmodifiableSet());
        if (authorities.stream().noneMatch(authority -> authority.startsWith("PLATFORM_"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform identity required");
        }
        if (expectedActor != null && !expectedActor.equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform actor does not match authenticated identity");
        }
        if (!authorities.contains(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing required permission: " + permission);
        }
        return authentication;
    }
}
