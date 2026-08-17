package rozov.nikita.linkd.ratelimiter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limiter")
@Getter
@Setter
public class RateLimiterUtil {
    private long capacity;
    private long refillTokens;
    private long refillPeriodNanos;
}
