package rozov.nikita.linkd.utility;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "config")
@Getter
@Setter
public class PropertyUtil {
    private String baseUrl;
    private String chars;
    private int length;
    private int base;
    private long scrambleNumberPos;
    private long scrambleNumberNeg;
    private Instant defaultExpiresAt;
    private Map<String, Long> cache;
    private String lockPrefix = "lock:";
    private long lockTtlSeconds = 5;
    private long lockPollIntervalMs = 50;
    private long lockMaxWaitMs = 150;
    private String redisUnlockScript;
    private long idempotencyMaxWaitMs;
    private long idempotencyPollIntervalMs;

}
