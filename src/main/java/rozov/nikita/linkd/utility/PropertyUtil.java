package rozov.nikita.linkd.utility;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

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
}
