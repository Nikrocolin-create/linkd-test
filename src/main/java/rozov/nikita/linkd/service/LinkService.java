package rozov.nikita.linkd.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.utility.CodeGenerator;
import rozov.nikita.linkd.utility.PropertyUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class LinkService {
    private final LinkRepository repository;
    private final PropertyUtil props;
    private final CodeGenerator codeGenerator;
    private final RedisTemplate<String, String> redisTemplate;
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
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        String url = valueOps.get(shortCode);
        if (url != null) {
            return url;
        } else {
            Link value = repository.findByShortCodeAndExpiresAtAfter(shortCode, Instant.now()).orElseThrow(() -> new EntityNotFoundException("Code not found: " + shortCode));
            long remainingTtl = value.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            long ttl = Math.min(props.getCache().get("ttl"), remainingTtl);
            valueOps.set(shortCode, value.getLongUrl(), ttl, TimeUnit.SECONDS);
            return value.getLongUrl();
        }

    }

    private String generateShortUrl(String shortCode) {
        return props.getBaseUrl() + shortCode;
    }
}
