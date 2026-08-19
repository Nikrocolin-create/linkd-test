package rozov.nikita.linkd.ratelimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(RateLimiterUtil.class)
@ConditionalOnProperty(prefix="rate-limiter", name="enabled", havingValue  = "true")
@Slf4j
public class RateLimiterConfig {

    @Bean
    public FilterRegistrationBean<RateLimiterFilter> rateLimiterFilter(RateLimiterUtil util) {
        log.debug("Filter bean initialized");
        FilterRegistrationBean<RateLimiterFilter> filter = new FilterRegistrationBean<>(new RateLimiterFilter(util));
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        filter.addUrlPatterns("/api/*");
        return filter;
    }
//    @Bean
//    public RateLimiterFilter rateLimiterFilter(RateLimiterUtil util) {
//        log.info("Filter bean initialized");
//        RateLimiterFilter filter = new RateLimiterFilter(util);
//        return filter;
//    }
}
