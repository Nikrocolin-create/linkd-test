package rozov.nikita.linkd.ratelimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import rozov.nikita.linkd.exception.TooManyRequestsException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


//@Component
//@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class RateLimiterFilter extends OncePerRequestFilter {
    private final RateLimiterUtil rateLimiterUtil;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String key = request.getRemoteAddr();
        log.debug("Rate Limiter filter chain for address {}", key);
        TokenBucket bucket = buckets.computeIfAbsent(key, (k) -> new TokenBucket(rateLimiterUtil.getCapacity(),
                rateLimiterUtil.getRefillPeriodNanos(), rateLimiterUtil.getRefillTokens(), new AtomicLong(rateLimiterUtil.getCapacity()), new AtomicLong(System.nanoTime())));
        try {
            bucket.tryConsume();
            filterChain.doFilter(request, response);
        }  catch (TooManyRequestsException ex) {
            log.warn("Rate limit exceeded for {}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "1");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                {"status":429,"title":"Too Many Requests","detail":"Rate limit exceeded"}""");
        }

    }
}
