package rozov.nikita.linkd;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.utility.PropertyUtil;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
//@AllArgsConstructor
class LinkdApplicationTests {
    @Autowired
    private LinkRepository repository;
    @Autowired
    private PropertyUtil props;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
	@Test
	void contextLoads() {
	}

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
        Link inserted = repository.findByShortCode(shortCode).orElse(null);
        assertNotNull(inserted);
        assertNotNull(inserted.getId());
        assertEquals(inserted.getShortCode(), link.getShortCode());
    }
    @Test
    public void restTemplate404 () throws Exception {
        mockMvc.perform(get("/api/v1/AAAAAAAA"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void restTemplateSuccess () throws Exception {
        String body = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkReq("https://www.google.com/", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LinkResp resp = objectMapper.readValue(body, LinkResp.class);

        mockMvc.perform(get(resp.getShortUrl()))
                .andExpect(status().is3xxRedirection());
    }
}
