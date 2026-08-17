package rozov.nikita.linkd.ratelimiter;

import lombok.AllArgsConstructor;
import rozov.nikita.linkd.exception.TooManyRequestsException;

import java.util.concurrent.atomic.AtomicLong;

@AllArgsConstructor
public class TokenBucket {
    private long capacity;
    private long refillTokens;
    private long refillPeriodNanos;

    private AtomicLong tokens;
    private AtomicLong lastRefillTimestamp;

    public void tryConsume() {
        refill();
        if (tokens.get() <= 0) {
            throw new TooManyRequestsException("Too many requests, wait before trying again");
        }
        tokens.decrementAndGet();
    }
    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillTimestamp.get();

        long newTokens = refillTokens * (now-last)/refillPeriodNanos;
        if (newTokens < 0) return;

        // ВАЖНО: двигаем время не на now, а ровно на «отоваренный» интервал,
        // иначе остаток < 1 токена каждый раз теряется и реальная скорость проседает
        long newLast = last + newTokens * refillPeriodNanos / refillTokens;

        if (lastRefillTimestamp.compareAndSet(last, newLast)) {
            tokens.updateAndGet(t->Math.min(tokens.get() + newTokens, capacity));
        }
    }
}
