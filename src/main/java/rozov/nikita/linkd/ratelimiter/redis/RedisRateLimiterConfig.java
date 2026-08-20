package rozov.nikita.linkd.ratelimiter.redis;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import rozov.nikita.linkd.ratelimiter.RateLimiterFilter;
import rozov.nikita.linkd.ratelimiter.RateLimiterProperties;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
@ConditionalOnProperty(prefix="rate-limiter", name="enabled", havingValue  = "true")
@ConditionalOnProperty(prefix="rate-limiter", name="type", havingValue  = "redis")
@Slf4j
public class RedisRateLimiterConfig {
    @Bean
    public FilterRegistrationBean<RateLimiterFilter> rateLimiterFilter(RateLimiterProperties util, ProxyManager<String> proxyManager) {
        log.debug("Filter bean initialized");
        FilterRegistrationBean<RateLimiterFilter> filter = new FilterRegistrationBean<>(new RateLimiterFilter(new RedisTokenBucketService(proxyManager, util)));
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        filter.addUrlPatterns("/api/*");
        return filter;
    }
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> rateLimiterConnection(
            RedisConnectionFactory factory) {

        if (!(factory instanceof LettuceConnectionFactory lettuce)) {
            throw new IllegalStateException("Expected LettuceConnectionFactory, not " + factory.getClass());
        }
        if (!(lettuce.getRequiredNativeClient() instanceof RedisClient client)) {
            throw new IllegalStateException("Redis Cluster needs other builder");
        }
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    public ProxyManager<String> proxyManager(StatefulRedisConnection<String, byte[]> connection,
                                             RateLimiterProperties props) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.of(props.getRefillPeriodNanos(), ChronoUnit.NANOS).plusMinutes(1)))
                .build();
    }
}
