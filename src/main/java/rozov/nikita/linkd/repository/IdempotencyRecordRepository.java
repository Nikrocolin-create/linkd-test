package rozov.nikita.linkd.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rozov.nikita.linkd.domain.IdempotencyRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByIdAndExpiresAtAfter(UUID id, java.time.Instant now);
    @Modifying
    @Query(value = "INSERT INTO idempotency (id, status, created_at, expires_at) VALUES (:key, 'IN_PROGRESS', now(), :lease) " +
            " ON CONFLICT (id) DO UPDATE SET status = 'IN_PROGRESS', " +
            "       created_at = now(), " +
            "       expires_at = :lease " +
            " WHERE idempotency.expires_at < now()", nativeQuery = true)
    int claim(@Param("key") UUID key, @Param("lease") Instant lease);
}
