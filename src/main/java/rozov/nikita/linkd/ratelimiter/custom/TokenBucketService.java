package rozov.nikita.linkd.ratelimiter.custom;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.extern.slf4j.Slf4j;
import rozov.nikita.linkd.ratelimiter.RateLimiterProperties;
import rozov.nikita.linkd.ratelimiter.TokenBucketInterface;

import java.time.Duration;

@Slf4j
public class TokenBucketService implements TokenBucketInterface {
    private final RateLimiterProperties rateLimiterProperties;
    private final Cache<String, TokenBucket> buckets;
    public TokenBucketService(RateLimiterProperties rateLimiterProperties) {
        this.rateLimiterProperties = rateLimiterProperties;
        buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(rateLimiterProperties.getEvictionPeriodSeconds()))
                .maximumSize(rateLimiterProperties.getMaximumBuckets())
                .scheduler(Scheduler.systemScheduler())
                .build();
    }

    @Override
    public void tryConsume(String key) {
        TokenBucket bucket = buckets.get(key, k -> new TokenBucket(rateLimiterProperties.getCapacity(),
                rateLimiterProperties.getRefillPeriodNanos(), rateLimiterProperties.getRefillTokens(), rateLimiterProperties.getCapacity(), System.nanoTime()));
        bucket.tryConsume();
    }
}

