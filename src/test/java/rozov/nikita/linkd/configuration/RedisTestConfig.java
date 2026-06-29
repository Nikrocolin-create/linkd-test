package rozov.nikita.linkd.configuration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

@TestConfiguration(proxyBeanMethods = false)
public class RedisTestConfig {

    @Bean
    @ServiceConnection(name="redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis").withExposedPorts(6379);
    }

}
