package rozov.nikita.linkd.service;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.utility.CodeGenerator;

import java.time.Instant;

import static rozov.nikita.linkd.utility.CodeGenerator.scramble;

@Service
@AllArgsConstructor
public class LinkService {
    private final LinkRepository repository;
    @Transactional
    public LinkResp create(CreateLinkReq req) {
        String shortCode = "********";
        Link link = Link.builder()
                .shortCode(shortCode)
                .longUrl(req.getUrl())
                .createdAt(Instant.now())
                .build();
        link = repository.save(link);
        link.setShortCode(CodeGenerator.encode(link.getId()));
        link = repository.save(link);
        return new LinkResp(link.getShortCode(), generateShortUrl(link.getShortCode()), Instant.now().plusSeconds(req.getTtl())); // todo expiresAt
    }

    public String getLongUrl(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(Link::getLongUrl)
                .orElseThrow(() -> new RuntimeException("Not found: " + shortCode)); // todo 404
    }


    private String generateShortUrl(String shortCode) {
        return "http://localhost:8080/" + shortCode; // todo @ConfigurationProperties
    }
    // Knuth's multiplicative constant 64-bit: 6364136223846793005


}
