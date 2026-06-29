package rozov.nikita.linkd;

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
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
public class RedisTests {
    @Autowired
    private MockMvc mockMvc;
    @MockitoSpyBean
    RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
//    @Autowired
//    private LinkRepository linkRepository;
    @MockitoSpyBean
    private LinkRepository linkRepository;

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
        // 2) заставляем template отдавать именно его (doReturn — для spy!)
        doReturn(spyOps).when(redisTemplate).opsForValue();

        // ... два GET ...

        String body = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkReq("https://www.google1.com/", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp resp = objectMapper.readValue(body, LinkResp.class);

        mockMvc.perform(get(resp.getShortUrl())).andReturn().getResponse();
        mockMvc.perform(get(resp.getShortUrl())).andReturn().getResponse();

//        ValueOperations<String, String> spyRedis = Mockito.spy(redisTemplate.opsForValue());
        verify(linkRepository, Mockito.times(1)).findByShortCodeAndExpiresAtAfter(any(),any());
        verify(spyOps, Mockito.times(2)).get(any());
    }
}
