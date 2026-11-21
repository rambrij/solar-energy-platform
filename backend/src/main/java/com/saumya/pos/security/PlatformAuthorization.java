package com.saumya.pos.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Platform guard requiring one explicit Platform actor and Platform-only grants. */
public final class PlatformAuthorization {
    private PlatformAuthorization() {}

    public static Authentication require(String permission,String expectedActor) {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||"anonymousUser".equals(auth.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
        Set<String> authorities=auth.getAuthorities().stream().map(a->a.getAuthority()).collect(Collectors.toUnmodifiableSet());
        long markerCount=Set.of(AuthorizationContext.ACTOR_CONTEXT_PLATFORM,AuthorizationContext.ACTOR_CONTEXT_VENDOR,
            AuthorizationContext.ACTOR_CONTEXT_CUSTOMER).stream().filter(authorities::contains).count();
        long allMarkerCount=authorities.stream().filter(a->a.startsWith("ACTOR_CONTEXT_")).count();
        if(markerCount!=1||allMarkerCount!=1||!authorities.contains(AuthorizationContext.ACTOR_CONTEXT_PLATFORM))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Explicit Platform actor context required");
        if(authorities.stream().anyMatch(a->!a.equals(AuthorizationContext.ACTOR_CONTEXT_PLATFORM)&&!a.startsWith("PLATFORM_")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform context cannot contain commercial permissions");
        if(auth.getDetails() instanceof ActorSessionDetails details
            &&(!details.actorType().equals("PLATFORM")
                ||details.tenantId()!=null||details.membershipId()!=null))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform actor scope is inconsistent");
        if(expectedActor!=null&&!expectedActor.equals(auth.getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform actor does not match authenticated identity");
        if(!authorities.contains(permission))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Missing required permission: "+permission);
        return auth;
    }
}
