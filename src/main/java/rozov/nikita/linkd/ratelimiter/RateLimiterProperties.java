package rozov.nikita.linkd.ratelimiter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limiter")
@Getter
@Setter
public class RateLimiterProperties {
    private String type;
    private long capacity;
    private long refillTokens;
    private long refillPeriodNanos;
    private int evictionPeriodSeconds;
    private int maximumBuckets;
}
