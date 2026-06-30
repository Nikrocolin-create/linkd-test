package rozov.nikita.linkd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.utility.TestcontainersConfiguration;
import rozov.nikita.linkd.configuration.PostgresTestConfig;
import rozov.nikita.linkd.configuration.RedisTestConfig;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@Import({PostgresTestConfig.class, RedisTestConfig.class})
@AutoConfigureMockMvc
public class RedisTests {
    @Autowired
    private MockMvc mockMvc;
    @MockitoSpyBean
    RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private LinkRepository linkRepository;
    @BeforeEach
    public void setUp() {
        // Clear the Redis cache before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    @Test
    public void setGetTest() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        ops.set("key1", "val1");
        String v = ops.get("key1");
        assertEquals("val1", v);
    }

    @Test
    public void redisMissHitTest() throws Exception {
        ValueOperations<String, String> spyOps = Mockito.spy(redisTemplate.opsForValue());
        doReturn(spyOps).when(redisTemplate).opsForValue();

        String body = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkReq("https://www.google1.com/", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp resp = objectMapper.readValue(body, LinkResp.class);

        mockMvc.perform(get(resp.getShortUrl())).andReturn().getResponse();
        mockMvc.perform(get(resp.getShortUrl())).andReturn().getResponse();

        verify(linkRepository, Mockito.times(1)).findByShortCodeAndExpiresAtAfter(any(),any());
        verify(spyOps, Mockito.times(2)).get(any());
    }

    @Test
    public void redisDeleteTest() throws Exception {
        String longUrl = "https://www.google2.com/";
        ValueOperations<String, String> spyOps = Mockito.spy(redisTemplate.opsForValue());
        doReturn(spyOps).when(redisTemplate).opsForValue();

        String body = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkReq(longUrl, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp resp = objectMapper.readValue(body, LinkResp.class);

        mockMvc.perform(get(resp.getShortUrl())).andReturn().getResponse();
        assertEquals(longUrl, spyOps.get(resp.getShortCode()));

        mockMvc.perform(delete("/api/v1/" + resp.getShortCode()))
                .andExpect(status().isNoContent());
        assertNull(spyOps.get(resp.getShortCode()));
    }
    @Test
    public void cacheWarmingTest() throws Exception {
        String longUrl = "https://www.google2.com/";
        ValueOperations<String, String> spyOps = Mockito.spy(redisTemplate.opsForValue());
        doReturn(spyOps).when(redisTemplate).opsForValue();

        String body = mockMvc.perform(post("/api/v1/links")
                        .param("cacheWarming", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkReq(longUrl, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp resp = objectMapper.readValue(body, LinkResp.class);

        assertEquals(longUrl, spyOps.get(resp.getShortCode()));
    }
}
