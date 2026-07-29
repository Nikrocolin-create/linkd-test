package rozov.nikita.linkd.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rozov.nikita.linkd.domain.IdempotencyRecord;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByIdAndExpiresAtAfter(UUID id, java.time.Instant now);
}
