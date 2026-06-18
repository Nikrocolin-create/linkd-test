package rozov.nikita.linkd.service;


import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import rozov.nikita.linkd.domain.Link;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.repository.LinkRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class LinkService {
    private final LinkRepository repository;

    public LinkResp create(CreateLinkReq req) {
        String shortCode = generateShortCode();
        Link link = Link.builder().
                shortCode(shortCode).
                customAlias(req.getCustomAlias()).
                longUrl(req.getUrl()).
                ttl(req.getTtl()).
                createdAt(Timestamp.valueOf(LocalDateTime.now())).
                build(); //todo mapper
        try {
            repository.save(link);
        } catch (Exception e) {
            cancelShortCode(shortCode);
            throw new RuntimeException("Resource was not created: " + e.getMessage()); //todo errors
        }
        return new LinkResp(link.getShortCode(),
                generateShortUrl(shortCode),
                new Timestamp(link.getCreatedAt().getTime() + link.getTtl()));
    }
    public LinkResp getLink(String shortCode) {
        Link link = repository.findByShortCode(shortCode);
        return new LinkResp(link.getShortCode(),
                generateShortUrl(shortCode),
                new Timestamp(link.getCreatedAt().getTime() + link.getTtl()));
    }
    private String generateShortCode() {
        return "todo"; // todo
    }

    private String generateShortUrl(String shortCode) {
        return "http://localhost:8080/" + shortCode;//todo
    }

    private void cancelShortCode(String shortCode) {
        // compensation transaction for shortCode
    }

}
