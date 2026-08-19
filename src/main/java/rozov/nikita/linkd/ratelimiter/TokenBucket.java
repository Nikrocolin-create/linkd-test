package rozov.nikita.linkd.ratelimiter;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import rozov.nikita.linkd.exception.TooManyRequestsException;

import java.util.concurrent.atomic.AtomicLong;

@AllArgsConstructor
@Slf4j
public class TokenBucket {
    private long capacity;
    private long refillTokens;
    private long refillPeriodNanos;

    private AtomicLong tokens;
    private AtomicLong lastRefillTimestamp;

    public void tryConsume() {
        refill();
        long prev = tokens.getAndUpdate(t -> t > 0 ? t-1 : t);
        if (prev <= 0) {
            throw new TooManyRequestsException("Too many requests, wait before trying again");
        }
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
//            log.info("updated");
            tokens.updateAndGet(t->Math.min(t + newTokens, capacity));
        }
    }
}
