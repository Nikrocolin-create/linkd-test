package rozov.nikita.linkd;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import rozov.nikita.linkd.configuration.TestcontainersConfiguration;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.repository.IdempotencyRecordRepository;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.service.LinkService;
import rozov.nikita.linkd.utility.ConfigProperties;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties = {
                "rate-limiter.enabled=true",
                "rate-limiter.capacity=2",
                "rate-limiter.refill_tokens=1",
                "rate-limiter.type=redis",
                "rate-limiter.refill_period_nanos=500000000000000"}
)
@AutoConfigureMockMvc
class RateLimiterRedisTests {
    @MockitoSpyBean
    private LinkRepository repository;
    @MockitoSpyBean
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @MockitoSpyBean
    private LinkService service;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ConfigProperties props;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void contextLoads() {
    }

    @AfterEach
    public void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        meterRegistry.clear();
        repository.deleteAll();
        idempotencyRecordRepository.deleteAll();
    }

    @Test
    public void rateLimiterTest() throws Exception {
        int n = 20;
        String idempotencyKey = UUID.randomUUID().toString();
        String json = objectMapper.writeValueAsString(
                new CreateLinkReq("https://example.com/", null));

        record Result(int status, String body) {
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            CountDownLatch startGate = new CountDownLatch(1);
            List<Callable<Result>> tasks = IntStream.range(0, n)
                    .mapToObj(i -> (Callable<Result>) () -> {
                        startGate.await();
                        var response = mockMvc.perform(post("/api/v1/links")
                                        .header("Idempotency-Key", idempotencyKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                                .andReturn().getResponse();
                        return new Result(response.getStatus(), response.getContentAsString());
                    })
                    .toList();

            List<Future<Result>> futures = tasks.stream().map(pool::submit).toList();
            startGate.countDown();

            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) results.add(f.get());
            pool.shutdown();
            results.stream().forEach(System.out::println);
            assertEquals(2, results.stream().map(Result::body).distinct().count(), "все тела должны совпадать");
            assertEquals(2, results.stream().map(Result::status).filter((i)->i.equals(201)).count());
            assertEquals(Set.of(201, 429), results.stream().map(Result::status).distinct().collect(Collectors.toSet()));
        }
    }

}
