package rozov.nikita.linkd.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rozov.nikita.linkd.domain.Link;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Link findByShortCode(String shortCode);
}
