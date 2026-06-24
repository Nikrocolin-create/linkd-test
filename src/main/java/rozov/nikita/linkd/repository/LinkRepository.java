package rozov.nikita.linkd.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import rozov.nikita.linkd.domain.Link;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
    @Query(value = "SELECT nextval('links_id_seq')", nativeQuery = true)
    Long nextId();
}
