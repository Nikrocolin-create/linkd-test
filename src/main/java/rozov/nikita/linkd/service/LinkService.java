package rozov.nikita.linkd.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;

import java.time.Instant;

@Service
@AllArgsConstructor
public class LinkService {
    private final LinkRepository repository;

    public LinkResp create(CreateLinkReq req) {
        String shortCode = generateShortCode();
        Link link = Link.builder()
                .shortCode(shortCode)
                .longUrl(req.getUrl())
                .createdAt(Instant.now())
                .build();
        repository.save(link);
        return new LinkResp(link.getShortCode(), generateShortUrl(shortCode), Instant.now().plusSeconds(req.getTtl())); // todo expiresAt
    }

    public String getLongUrl(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(Link::getLongUrl)
                .orElseThrow(() -> new RuntimeException("Not found: " + shortCode)); // todo 404
    }

    private String generateShortCode() {
        return "local"; // todo base62
    }

    private String generateShortUrl(String shortCode) {
        return "http://localhost:8080/" + shortCode; // todo @ConfigurationProperties
    }
}
