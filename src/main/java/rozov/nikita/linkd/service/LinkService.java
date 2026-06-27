package rozov.nikita.linkd.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.utility.CodeGenerator;
import rozov.nikita.linkd.utility.PropertyUtil;

import java.time.Instant;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LinkService {
    private final LinkRepository repository;
    private final PropertyUtil props;
    private final CodeGenerator codeGenerator;
    @Transactional
    public LinkResp create(CreateLinkReq req) {
        Long id = repository.nextId();
        String shortCode = codeGenerator.encode(id);
        Instant expiresAt = req.getTtl() != null
                ? Instant.now().plusSeconds(req.getTtl())
                : props.getDefaultExpiresAt();
        Link link = Link.builder()
                .id(id)
                .isNew(true)
                .shortCode(shortCode)
                .longUrl(req.getUrl())
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
        Optional<Link> existing = repository.findByLongUrl(req.getUrl());
        if (existing.isPresent()) {
            Link found = existing.get();
            return new LinkResp(found.getShortCode(), generateShortUrl(found.getShortCode()), expiresAt);
        }
        link = repository.save(link);
        return new LinkResp(link.getShortCode(), generateShortUrl(link.getShortCode()), expiresAt);
    }

    public String getLongUrl(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(Link::getLongUrl)
                .orElseThrow(() -> new EntityNotFoundException("Code not found: " + shortCode));
    }


    private String generateShortUrl(String shortCode) {
        return props.getBaseUrl() + shortCode;
    }
    // Knuth's multiplicative constant 64-bit: 6364136223846793005



}
