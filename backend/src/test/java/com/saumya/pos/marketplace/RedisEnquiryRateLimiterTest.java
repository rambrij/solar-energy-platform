package com.saumya.pos.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

class RedisEnquiryRateLimiterTest {
    private static final Instant NOW=Instant.parse("2026-11-28T00:00:00Z");
    private static final String HASH="a".repeat(64);

    @Test void permitsFirstFiveRequestsAndRejectsTheSixthAtomically() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        doReturn(5L).when(redis).execute(any(RedisScript.class),anyList(),any(Object[].class));
        var limiter=new RedisEnquiryRateLimiter(redis,true);
        assertThat(limiter.tryAcquire(71,HASH,NOW)).isTrue();

        doReturn(6L).when(redis).execute(any(RedisScript.class),anyList(),any(Object[].class));
        assertThat(limiter.tryAcquire(71,HASH,NOW)).isFalse();
    }

    @Test void isDisabledWithoutRedisConfigurationAndDoesNotTouchTheClient() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        var limiter=new RedisEnquiryRateLimiter(redis,false);
        assertThat(limiter.enabled()).isFalse();
        assertThat(limiter.tryAcquire(71,HASH,NOW)).isTrue();
        verifyNoInteractions(redis);
    }

    @Test void failsClosedWhenRedisCannotReturnALimitDecision() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        doReturn(null).when(redis).execute(any(RedisScript.class),anyList(),any(Object[].class));
        var limiter=new RedisEnquiryRateLimiter(redis,true);
        assertThatThrownBy(()->limiter.tryAcquire(71,HASH,NOW))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Marketplace rate limiting is unavailable");
    }
}
