package rozov.nikita.linkd.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "links")
public class Link implements Persistable<String> {
    @Transient
    private boolean isNew = false;
    @Override public boolean isNew() { return isNew; }
    @Override public String getId() { return shortCode; }

    @Id
    @Column(name = "short_code", nullable = false, length = 8)
    private String shortCode;

    @Column(name = "long_url", nullable = false, unique = true)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
