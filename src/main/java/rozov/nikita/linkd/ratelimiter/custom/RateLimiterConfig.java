package rozov.nikita.linkd.ratelimiter.custom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import rozov.nikita.linkd.ratelimiter.RateLimiterFilter;
import rozov.nikita.linkd.ratelimiter.RateLimiterProperties;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
@ConditionalOnProperty(prefix="rate-limiter", name="enabled", havingValue  = "true")
@ConditionalOnProperty(prefix="rate-limiter", name="type", havingValue  = "custom")
@Slf4j
public class RateLimiterConfig {
    @Bean
    public FilterRegistrationBean<RateLimiterFilter> rateLimiterFilter(RateLimiterProperties util) {
        log.debug("Filter bean initialized");
        FilterRegistrationBean<RateLimiterFilter> filter = new FilterRegistrationBean<>(new RateLimiterFilter(new TokenBucketService(util)));
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        filter.addUrlPatterns("/api/*");
        return filter;
    }

}
