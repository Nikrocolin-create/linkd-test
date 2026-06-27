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
public class Link  implements Persistable<Long> {
    @Transient
    private boolean isNew = false;
    @Override public boolean isNew() { return isNew; }

    @Id
//    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "links_seq")
//    @SequenceGenerator(name = "links_seq", sequenceName = "links_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 8)
    private String shortCode;

    @Column(name = "long_url", nullable = false, unique = true)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
