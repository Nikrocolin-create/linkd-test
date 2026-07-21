package rozov.nikita.linkd;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.utility.TestcontainersConfiguration;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.utility.PropertyUtil;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LinkdApplicationTests {
    @Autowired
    private LinkRepository repository;
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

    @Test
    public void insertionDBTest() {
        String shortCode = "test";

        Instant expiresAt = props.getDefaultExpiresAt();
        Link link = Link.builder()
                .id(1L)
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

        ExecutorService pool = Executors.newFixedThreadPool(50);
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
        for (Future<Void> f: futures) f.get();
        pool.shutdown(); //blocks pool to accept new tasks
        assertEquals(49, meterRegistry.counter("cache.hits", "cacheName","links").count());
        assertEquals(1, meterRegistry.counter("cache.misses", "cacheName","links").count());
    }
}
