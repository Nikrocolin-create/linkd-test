package rozov.nikita.linkd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rozov.nikita.linkd.domain.IdempotencyRecord;
import rozov.nikita.linkd.domain.IdempotencyStatus;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.exception.IdempotencyTimeoutException;
import rozov.nikita.linkd.repository.IdempotencyRecordRepository;
import rozov.nikita.linkd.utility.PropertyUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PropertyUtil props;


    @Transactional
    public boolean tryClaim(UUID idempotencyKey) {
        return idempotencyRecordRepository.claim(idempotencyKey, Instant.now().plusSeconds(props.getIdempotencyLeaseSeconds())) > 0;
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
