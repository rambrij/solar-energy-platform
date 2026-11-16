package com.saumya.pos.platform;

import com.saumya.pos.security.AuthorizationContext;
import java.time.Instant;
import java.time.Clock;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/platform/health")
public class PlatformHealthController {
    public record PlatformHealth(String status,Instant checkedAt){}
    private final HealthEndpoint health;private final AuthorizationContext auth;private final Clock clock;
    public PlatformHealthController(HealthEndpoint health,AuthorizationContext auth,Clock clock){this.health=health;this.auth=auth;this.clock=clock;}
    @GetMapping public PlatformHealth get(){
        var ctx=auth.current();if(!ctx.isPlatformUser())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform identity required");
        ctx.requirePermission("PLATFORM_ANALYTICS_VIEW");
        return new PlatformHealth(health.health().getStatus().getCode(),clock.instant());
    }
}
