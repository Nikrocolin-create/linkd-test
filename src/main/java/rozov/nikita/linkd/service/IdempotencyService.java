package rozov.nikita.linkd.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rozov.nikita.linkd.domain.IdempotencyRecord;
import rozov.nikita.linkd.domain.IdempotencyStatus;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.exception.IdempotencyTimeoutException;
import rozov.nikita.linkd.repository.IdempotencyRecordRepository;
import rozov.nikita.linkd.utility.CodeGenerator;
import rozov.nikita.linkd.utility.PropertyUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static java.lang.Thread.sleep;

@Service
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PropertyUtil props;
    private final LinkService linkService;
    public IdempotencyService(LinkService linkService, IdempotencyRecordRepository idempotencyRecordRepository, PropertyUtil props, CodeGenerator codeGenerator,
                              RedisTemplate<String, String> redisTemplate, MeterRegistry meterRegistry) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.props = props;
        this.linkService = linkService;
    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public LinkResp claim(UUID idempotencyKey) {
//        int claimed_rows = idempotencyRecordRepository.claim(idempotencyKey, Instant.now().plusMillis(props.getIdempotencyMaxWaitMs()));
//        log.info("Claimed rows: {}", claimed_rows);
//        if (claimed_rows == 0) {
//            IdempotencyRecord rec = idempotencyRecordRepository.findById(idempotencyKey).orElseThrow(
//                    () -> new RuntimeException("something strange")
//            );
//            if (IdempotencyStatus.DONE.equals(rec.getStatus())) return rec.getResponse();
//            else {
//                try {
//                    Instant finish = Instant.now().plusMillis(props.getIdempotencyMaxWaitMs());
//                    while (finish.isAfter(Instant.now()) && rec == null) {
//                        sleep(props.getIdempotencyPollIntervalMs());
//                    }
//
//                    rec = idempotencyRecordRepository.findById(idempotencyKey).orElseThrow(
//                            () -> new RuntimeException("something strange")
//                    );
//                    if (IdempotencyStatus.DONE.equals(rec.getStatus())) return rec.getResponse();
//                    else throw new IdempotencyTimeoutException("Idempotency key timeout, try again later");
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    throw new IllegalStateException("Interrupted while waiting for cache lock", e);
//                }
//            }
//        }
//        return null;
//    }
    @Transactional
    public boolean tryClaim(UUID idempotencyKey) {
        return idempotencyRecordRepository.claim(idempotencyKey, Instant.now().plusSeconds(1)) > 0;
    }
    public Optional<LinkResp> readResult(UUID idempotencyKey) {
        IdempotencyRecord rec = idempotencyRecordRepository.findById(idempotencyKey).orElseThrow(
                () -> new IdempotencyTimeoutException("The record with idempotency key doesn't exist")
        );
        if (rec.getStatus() == IdempotencyStatus.DONE) return Optional.ofNullable(rec.getResponse());
        else return Optional.empty();
    }
    public void deleteById(UUID id){
        idempotencyRecordRepository.deleteById(id);
    }
}
