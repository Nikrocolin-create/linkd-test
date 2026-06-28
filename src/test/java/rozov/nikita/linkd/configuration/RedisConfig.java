package rozov.nikita.linkd.configuration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean
    @ServiceConnection(name="redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis").withExposedPorts(6379);
    }

}
