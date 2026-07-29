package rozov.nikita.linkd.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import rozov.nikita.linkd.dto.LinkResp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency")
@Data
public class IdempotencyRecord implements Persistable<UUID> {
    @Transient
    private boolean isNew = false;
    @Override
    public boolean isNew() {
        return isNew; // Implement this method based on your requirements
    }
    @Id
    private UUID id;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private LinkResp response;
    private Instant createdAt;
    private Instant expiresAt;
}
