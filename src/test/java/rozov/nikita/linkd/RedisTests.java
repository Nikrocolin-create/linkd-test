package rozov.nikita.linkd;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import rozov.nikita.linkd.config.RedisConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;



@SpringBootTest
@Import(RedisConfig.class)
public class RedisTests {
    @Autowired
    RedisTemplate<String, String> redisTemplate;
    @Test
    public void setGetTest() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        ops.set("key1", "val1");
        String v = ops.get("key1");
        assertEquals("val1", v);
    }
}
