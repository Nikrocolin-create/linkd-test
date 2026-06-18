package rozov.nikita.linkd.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rozov.nikita.linkd.domain.Link;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
}
