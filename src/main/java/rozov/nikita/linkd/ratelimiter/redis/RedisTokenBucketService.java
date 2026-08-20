package rozov.nikita.linkd.ratelimiter.redis;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import rozov.nikita.linkd.exception.TooManyRequestsException;
import rozov.nikita.linkd.ratelimiter.RateLimiterProperties;
import rozov.nikita.linkd.ratelimiter.TokenBucketInterface;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

public class RedisTokenBucketService implements TokenBucketInterface {

        private final ProxyManager<String> proxyManager;
        private final Supplier<BucketConfiguration> configSupplier;

        public RedisTokenBucketService(ProxyManager<String> proxyManager, RateLimiterProperties props) {
            this.proxyManager = proxyManager;
            // supplier вызывается только при первом создании бакета в Redis
            this.configSupplier = () -> BucketConfiguration.builder()
                    .addLimit(limit -> limit
                            .capacity(props.getCapacity())
                            .refillGreedy(props.getCapacity(), Duration.of(props.getRefillPeriodNanos(), ChronoUnit.NANOS)))
                    .build();
        }

        public void tryConsume(String key) {
            Bucket bucket = proxyManager.getProxy("rl:" + key, configSupplier);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) throw new TooManyRequestsException("");
        }
}
