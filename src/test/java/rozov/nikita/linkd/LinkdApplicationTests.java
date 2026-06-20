package rozov.nikita.linkd;

import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.repository.LinkRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
//@AllArgsConstructor
class LinkdApplicationTests {
    @Autowired
    private LinkRepository repository;
	@Test
	void contextLoads() {
	}

    @Test
    public void insertionDBTest() {
        String shortCode = "test";
        Link link = Link.builder()
                .shortCode("test")
                .longUrl("https://test.test")
                .ttl(10000L)
                .createdAt(Instant.now())
                .build();
        repository.save(link);
        Link inserted = repository.findByShortCode(shortCode).orElse(null);
        assertNotNull(inserted);
        assertNotNull(inserted.getId());
        assertEquals(inserted.getShortCode(), link.getShortCode());
    }
}
