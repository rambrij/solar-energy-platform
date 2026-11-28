package com.saumya.pos.marketplace;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Distributed, tenant-isolated sliding-window limiter for anonymous marketplace enquiries.
 * The Lua script makes expiry, insertion and count one atomic Redis operation across app instances.
 */
@Component
public class RedisEnquiryRateLimiter {
    private static final int MAX_REQUESTS=5;
    private static final Duration WINDOW=Duration.ofMinutes(10);
    private static final DefaultRedisScript<Long> WINDOW_SCRIPT=new DefaultRedisScript<>();

    static {
        WINDOW_SCRIPT.setResultType(Long.class);
        WINDOW_SCRIPT.setScriptText("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local oldest = now - window
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', oldest)
            local total = redis.call('ZCARD', KEYS[1])
            if total < tonumber(ARGV[5]) then
              redis.call('ZADD', KEYS[1], 'NX', now, ARGV[3])
              total = total + 1
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return total
            """);
    }

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public RedisEnquiryRateLimiter(StringRedisTemplate redis,
            @Value("${app.rate-limit.redis-enabled:false}") boolean enabled) {
        this.redis=redis;
        this.enabled=enabled;
    }

    public boolean enabled(){return enabled;}

    public boolean tryAcquire(long tenantId,String requesterHash,Instant now) {
        if(!enabled)return true;
        if(tenantId<=0||requesterHash==null||!requesterHash.matches("[0-9a-f]{64}")||now==null)
            throw new IllegalArgumentException("Valid tenant, requester hash and clock instant are required");
        String key="saumya:rate:public-enquiry:v1:"+tenantId+":"+requesterHash;
        try {
            Long count=redis.execute(WINDOW_SCRIPT,List.of(key),
                Long.toString(now.toEpochMilli()),
                Long.toString(WINDOW.toMillis()),
                UUID.randomUUID().toString(),
                Long.toString(WINDOW.plusMinutes(1).toMillis()),
                Integer.toString(MAX_REQUESTS));
            if(count==null||count<0)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Marketplace rate limiting is unavailable");
            return count<=MAX_REQUESTS;
        } catch(DataAccessException unavailable) {
            // Fail closed: accepting unauthenticated traffic without a configured limiter
            // would silently remove the abuse-control boundary.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Marketplace rate limiting is unavailable",unavailable);
        }
    }
}
