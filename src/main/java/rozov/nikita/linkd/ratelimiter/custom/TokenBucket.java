package rozov.nikita.linkd.ratelimiter.custom;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import rozov.nikita.linkd.exception.TooManyRequestsException;

@AllArgsConstructor
@Slf4j
public class TokenBucket {
    private long capacity;
    private long refillPeriodNanos;
    private long refillTokens;

    private long tokens;
    private long lastRefillTimestamp;

    public synchronized void tryConsume() {
        long elapsed = System.nanoTime() - lastRefillTimestamp;
        if (elapsed >= refillPeriodNanos) {
            long periods = elapsed / refillPeriodNanos;
            long added = periods > capacity ? capacity : periods * refillTokens;
            tokens = Math.min(tokens + added, capacity);
            lastRefillTimestamp += periods * refillPeriodNanos;
        }
        if (tokens < 1) {
            throw new TooManyRequestsException("Too many requests, wait before trying again");
        }
        tokens = Math.max(tokens-1, 0);
    }
}

