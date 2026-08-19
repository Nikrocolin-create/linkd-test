package rozov.nikita.linkd;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import rozov.nikita.linkd.configuration.TestcontainersConfiguration;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.IdempotencyRecordRepository;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.service.LinkService;
import rozov.nikita.linkd.utility.PropertyUtil;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties =  {"rate-limiter.enabled=false"}
)
@AutoConfigureMockMvc
class LinkdApplicationTests {
    @MockitoSpyBean
    private LinkRepository repository;
    @MockitoSpyBean
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @MockitoSpyBean
    private LinkService service;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private PropertyUtil props;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MeterRegistry meterRegistry;
	@Test
	void contextLoads() {}

    @AfterEach
    public void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        meterRegistry.clear();
        repository.deleteAll();
        idempotencyRecordRepository.deleteAll();
    }
    @Test
    public void insertionDBTest() {
        String shortCode = "test";

        Instant expiresAt = props.getDefaultExpiresAt();
        Link link = Link.builder()
                .shortCode("test")
                .longUrl("https://test.test")
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
        repository.save(link);
        Link inserted = repository.findByShortCodeAndExpiresAtAfter(shortCode, Instant.now()).orElse(null);
        assertNotNull(inserted);
        assertNotNull(inserted.getId());
        assertEquals(inserted.getShortCode(), link.getShortCode());
    }
    @Test
    public void restTemplate404Test () throws Exception {
        mockMvc.perform(get("/api/v1/AAAAAAAA"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void restTemplateSuccessTest () throws Exception {
        String body = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkReq("https://www.google.com/", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp resp = objectMapper.readValue(body, LinkResp.class);

        mockMvc.perform(get(resp.getShortUrl()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    public void simultaneousRequestsCacheHitMissTets () throws Exception {
        String body = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkReq("https://www.yandex.com/", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp resp = objectMapper.readValue(body, LinkResp.class);

        try (ExecutorService pool = Executors.newFixedThreadPool(50)) {
            CountDownLatch startGate = new CountDownLatch(1);
            List<Callable<Void>> tasks = IntStream.range(0, 50)
                    .mapToObj(i -> (Callable<Void>) () -> {
                        startGate.await();
                        mockMvc.perform(get(resp.getShortUrl()))
                                .andExpect(status().is3xxRedirection());
                        return null;
                    })
                    .toList();
            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            startGate.countDown();
            for (Future<Void> f : futures) f.get();
            pool.shutdown(); //blocks pool to accept new tasks
            assertEquals(49, meterRegistry.counter("cache.hits", "cacheName", "links").count());
            assertEquals(1, meterRegistry.counter("cache.misses", "cacheName", "links").count());
        }
    }

    @Test
    public void equalsCustomAliases () throws Exception {
        CreateLinkReq req = new CreateLinkReq("https://www.yandex.com/", "custom", null);
        String json = objectMapper.writeValueAsString(req);


        mockMvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict());

        verify(service, Mockito.times(2)).create(any(), anyBoolean(), any());
    }

    @Test
    public void idempotencyTest () throws Exception {
        String idempotencyKey = "123e4567-e89b-12d3-a456-426614174000";
        CreateLinkReq req = new CreateLinkReq("https://www.yandex.com/", null);
        String json = objectMapper.writeValueAsString(req);

        String firstBody = mockMvc.perform(post("/api/v1/links")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String secondBody = mockMvc.perform(post("/api/v1/links")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp first = objectMapper.readValue(firstBody, LinkResp.class);
        LinkResp second = objectMapper.readValue(secondBody, LinkResp.class);

        assertEquals(first.getShortCode(), second.getShortCode());
        assertEquals(first.getShortUrl(), second.getShortUrl());
        assertEquals(first.getExpiresAt(), second.getExpiresAt());

        verify(repository, Mockito.times(0)).findByShortCodeAndExpiresAtAfter(any(), any());
        verify(idempotencyRecordRepository, Mockito.times(2)).claim(any(), any());
        verify(idempotencyRecordRepository, Mockito.times(1)).findById(any());
    }
    @Test
    public void concurrentIdempotencyTest() throws Exception {
        int n = 20;
        String idempotencyKey = UUID.randomUUID().toString();
        String json = objectMapper.writeValueAsString(
                new CreateLinkReq("https://example.com/", null));

        record Result(int status, String body) {}

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
            assertEquals(1, repository.count(), "в links должна быть ровно одна строка");
            assertEquals(1, idempotencyRecordRepository.count(), "ключ идемпотентности должен быть один");
            assertEquals(1, results.stream().map(Result::body).distinct().count(), "все тела должны совпадать");
            assertEquals(List.of(201), results.stream().map(Result::status).distinct().toList());
        }
    }

}
