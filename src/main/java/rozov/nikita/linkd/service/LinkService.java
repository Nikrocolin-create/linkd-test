package rozov.nikita.linkd.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rozov.nikita.linkd.domain.IdempotencyRecord;
import rozov.nikita.linkd.domain.IdempotencyStatus;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.IdempotencyRecordRepository;
import rozov.nikita.linkd.repository.LinkRepository;
import rozov.nikita.linkd.utility.CodeGenerator;
import rozov.nikita.linkd.utility.PropertyUtil;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

@Service
@Slf4j
public class LinkService {

    private final LinkRepository repository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PropertyUtil props;
    private final RedisTemplate<String, String> redisTemplate;
    private final CodeGenerator codeGenerator;
    private final RedisScript<Long> UNLOCK_SCRIPT;
    private final Counter cacheMissCounter;
    private final Counter cacheHitCounter;

    public LinkService(LinkRepository repository, IdempotencyRecordRepository idempotencyRecordRepository, PropertyUtil props, CodeGenerator codeGenerator,
                       RedisTemplate<String, String> redisTemplate, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.props = props;
        this.codeGenerator = codeGenerator;
        this.redisTemplate = redisTemplate;
        this.UNLOCK_SCRIPT = new DefaultRedisScript<>(props.getRedisUnlockScript(), Long.class);
        this.cacheHitCounter = meterRegistry.counter("cache.hits", "cacheName", "links");
        this.cacheMissCounter = meterRegistry.counter("cache.misses", "cacheName", "links");
    }


    @Transactional(timeoutString = "${config.creation_transaction_seconds:3}")
    public LinkResp create(CreateLinkReq req, boolean cacheEnabled, UUID idempotencyKey) {

        Long id = repository.nextId();
        String shortCode = generateShortCode(req.getCustomAlias(), id);
        Optional<Link> existing = repository.findByLongUrlAndExpiresAtAfter(req.getUrl(), Instant.now());
        if (existing.isPresent()) {
            Link found = existing.get();
            if (idempotencyKey != null) {
                idempotencySave(found, idempotencyKey);
            }
            return new LinkResp(found.getShortCode(), generateShortUrl(found.getShortCode()), found.getExpiresAt());
        }
        Link link = createLinkEntity(req, shortCode, id);
        link = repository.save(link);
        if (cacheEnabled) {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            ops.set(link.getShortCode(), req.getUrl(), link.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond(), TimeUnit.SECONDS);
        }
        if (idempotencyKey != null) {
            idempotencySave(link, idempotencyKey);
        }
        log.info("Created new link: {} -> {}", link.getShortCode(), req.getUrl());
        return new LinkResp(link.getShortCode(), generateShortUrl(link.getShortCode()), link.getExpiresAt());
    }

    private String generateShortCode(String customAlias, Long id) {
        String shortCode;
        if (customAlias != null && !customAlias.isEmpty()) {
            Optional<Link> existing = repository.findByShortCodeAndExpiresAtAfter(customAlias, Instant.now());
            if (existing.isPresent()) {
                throw new DataIntegrityViolationException("Custom alias already exists: " + customAlias);
            }
            shortCode = customAlias;
        } else {
            shortCode = codeGenerator.encode(id);
        }
        return shortCode;
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
                sleep(props.getLockPollIntervalMs());
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

    private Link createLinkEntity(CreateLinkReq req, String shortCode, Long id) {
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
        return link;
    }

    private IdempotencyRecord idempotencySave(Link link, UUID idempotencyKey) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(idempotencyKey);
        record.setResponse(new LinkResp(link.getShortCode(), generateShortUrl(link.getShortCode()), link.getExpiresAt()));
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plusSeconds(props.getIdempotencyRetentionSeconds()));
        record.setStatus(IdempotencyStatus.DONE);
        return idempotencyRecordRepository.save(record);
    }
}
