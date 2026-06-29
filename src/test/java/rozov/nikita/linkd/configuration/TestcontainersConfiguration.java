package rozov.nikita.linkd.configuration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods = false)
@Import({RedisTestConfig.class, PostgresTestConfig.class})
public class TestcontainersConfiguration {

}
