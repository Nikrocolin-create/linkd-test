package rozov.nikita.linkd.domain;

import jakarta.persistence.Entity;
import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Builder
public class Link {
    private Long id;
    private String shortCode;
    private String customAlias;
    private String longUrl;
    private Long ttl;
    private Timestamp createdAt;
}
