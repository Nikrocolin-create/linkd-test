package rozov.nikita.linkd.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.utility.CodeGenerator;
import rozov.nikita.linkd.utility.PropertyUtil;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkService {
    private static final String LOCK_PREFIX = "lock:";
    private static final long LOCK_TTL_SECONDS = 5;
    private static final long LOCK_POLL_INTERVAL_MS = 50;
    private static final long LOCK_MAX_WAIT_MS = 5000;

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else return 0 end",
            Long.class
    );

    private final LinkRepository repository;
    private final PropertyUtil props;
    private final CodeGenerator codeGenerator;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public LinkResp create(CreateLinkReq req, boolean cacheEnabled) {
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
        if (cacheEnabled) {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            ops.set(shortCode, req.getUrl(), expiresAt.getEpochSecond() - Instant.now().getEpochSecond(), TimeUnit.SECONDS);
        }
        log.info("Created new link: {} -> {}", shortCode, req.getUrl());
        return new LinkResp(link.getShortCode(), generateShortUrl(link.getShortCode()), expiresAt);
    }

    public String getLongUrl(String shortCode) {
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        String url = valueOps.get(shortCode);
        if (url != null) {
            log.info("Cache hit for link: {} -> {}", shortCode, url);
            return url;
        }
        return resolveWithLock(shortCode, valueOps);
    }

    private String resolveWithLock(String shortCode, ValueOperations<String, String> valueOps) {
        String lockKey = LOCK_PREFIX + shortCode;
        String lockToken = UUID.randomUUID().toString();

        boolean acquired = Boolean.TRUE.equals(
                valueOps.setIfAbsent(lockKey, lockToken, LOCK_TTL_SECONDS, TimeUnit.SECONDS));

        if (acquired) {
            try {
                Link value = repository.findByShortCodeAndExpiresAtAfter(shortCode, Instant.now())
                        .orElseThrow(() -> new EntityNotFoundException("Code not found: " + shortCode));
                long remainingTtl = value.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
                long ttl = Math.min(props.getCache().get("ttl"), remainingTtl);
                valueOps.set(shortCode, value.getLongUrl(), ttl, TimeUnit.SECONDS);
                log.info("Cache miss for link: {} -> {}", shortCode, value.getLongUrl());
                return value.getLongUrl();
            } finally {
                redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockToken);
            }
        }

        long waited = 0;
        while (waited < LOCK_MAX_WAIT_MS) {
            try {
                Thread.sleep(LOCK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for cache lock", e);
            }
            waited += LOCK_POLL_INTERVAL_MS;
            String cached = valueOps.get(shortCode);
            if (cached != null) {
                log.info("Cache populated by lock holder while waiting: {} -> {}", shortCode, cached);
                return cached;
            }
        }
        // lock holder never released in time (crashed?) — its TTL should have freed the key by now,
        // so retry: we'll either win the lock ourselves or read a value another retrier just wrote.
        return resolveWithLock(shortCode, valueOps);
    }

    public void deleteLink(String shortCode) {
        repository.findByShortCodeAndExpiresAtAfter(shortCode, Instant.now())
                .ifPresent(link -> {
                    redisTemplate.delete(shortCode);
                    repository.delete(link);
                    log.info("Deleted link with short code: {}", shortCode);
                });
    }
    private String generateShortUrl(String shortCode) {
        return props.getBaseUrl() + shortCode;
    }
}
