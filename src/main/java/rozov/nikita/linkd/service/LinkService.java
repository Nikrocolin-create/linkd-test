package rozov.nikita.linkd.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
@Slf4j
public class LinkService {

    private final LinkRepository repository;
    private final PropertyUtil props;
    private final RedisTemplate<String, String> redisTemplate;
    private final CodeGenerator codeGenerator;
    private final RedisScript<Long> UNLOCK_SCRIPT;
    private final Counter cacheMissCounter;
    private final Counter cacheHitCounter;
    public LinkService(LinkRepository repository, PropertyUtil props, CodeGenerator codeGenerator,
                       RedisTemplate<String, String> redisTemplate, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.props = props;
        this.codeGenerator = codeGenerator;
        this.redisTemplate = redisTemplate;
        this.UNLOCK_SCRIPT = new DefaultRedisScript<>(props.getRedisUnlockScript(), Long.class);
        this.cacheHitCounter = meterRegistry.counter("cache.hits", "cacheName","links");
        this.cacheMissCounter = meterRegistry.counter("cache.misses", "cacheName","links");
    }


    @Transactional
    public LinkResp create(CreateLinkReq req, boolean cacheEnabled) {
        Long id = repository.nextId();
        String shortCode;
        if (req.getCustomAlias() != null && !req.getCustomAlias().isEmpty()) {
            Optional<Link> existing = repository.findByShortCodeAndExpiresAtAfter(req.getCustomAlias(), Instant.now());
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Custom alias already exists: " + req.getCustomAlias());
            }
            shortCode = req.getCustomAlias();
        } else {
            shortCode = codeGenerator.encode(id);
        }
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
            this.cacheHitCounter.increment();
            return url;
        }
        return resolveWithLock(shortCode, valueOps);
    }

    private String resolveWithLock(String shortCode, ValueOperations<String, String> valueOps) {
        String lockKey = props.getLockPrefix() + shortCode;
        String lockToken = UUID.randomUUID().toString();

        boolean acquired = Boolean.TRUE.equals(
                valueOps.setIfAbsent(lockKey, lockToken, props.getLockTtlSeconds(), TimeUnit.SECONDS));

        if (acquired) {
            try {
                Link value = repository.findByShortCodeAndExpiresAtAfter(shortCode, Instant.now())
                        .orElseThrow(() -> new EntityNotFoundException("Code not found: " + shortCode));
                long remainingTtl = value.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
                long ttl = Math.min(props.getCache().get("ttl"), remainingTtl);
                valueOps.set(shortCode, value.getLongUrl(), ttl, TimeUnit.SECONDS);
                this.cacheMissCounter.increment();
                log.info("Cache miss for link: {} -> {}", shortCode, value.getLongUrl());
                return value.getLongUrl();
            } finally {
                redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockToken);
            }
        }

        long waited = 0;
        while (waited < props.getLockMaxWaitMs()) {
            try {
                Thread.sleep(props.getLockPollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for cache lock", e);
            }
            waited += props.getLockPollIntervalMs();
            String cached = valueOps.get(shortCode);
            if (cached != null) {
                log.info("Cache populated by lock holder while waiting: {} -> {}", shortCode, cached);
                this.cacheHitCounter.increment();
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
