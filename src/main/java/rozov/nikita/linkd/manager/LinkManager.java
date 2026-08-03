package rozov.nikita.linkd.manager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.exception.IdempotencyTimeoutException;
import rozov.nikita.linkd.service.IdempotencyService;
import rozov.nikita.linkd.service.LinkService;
import rozov.nikita.linkd.utility.PropertyUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static java.lang.Thread.sleep;

@Component
@RequiredArgsConstructor
public class LinkManager {
    private final IdempotencyService idempotencyService;
    private final LinkService linkService;
    private final PropertyUtil props;
    public LinkResp create(CreateLinkReq req, boolean cacheEnabled, UUID idempotencyKey) throws InterruptedException {
        LinkResp resp = null;
        if (idempotencyKey != null) {
            try {
                boolean claim = idempotencyService.tryClaim(idempotencyKey);
                if (!claim) {
                    Optional<LinkResp> oResp = idempotencyService.readResult(idempotencyKey);
                    if (oResp.isPresent()) {
                        return oResp.get();
                    } else {
                        Instant finish = Instant.now().plusMillis(props.getIdempotencyMaxWaitMs());
                        while (finish.isAfter(Instant.now())) {
                            sleep(props.getIdempotencyPollIntervalMs());
                            resp = idempotencyService.readResult(idempotencyKey).orElse(null);
                            if (resp != null) return resp;
                        }
                        throw new IdempotencyTimeoutException("Idempotency key timeout, try again later");
                    }
                } else {
                    try {
                        return linkService.create(req, cacheEnabled, idempotencyKey);
                    } catch (Exception e) {
                        idempotencyService.deleteById(idempotencyKey);
                        throw e;
                    }
                }
            } catch(InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            resp = linkService.create(req, cacheEnabled, idempotencyKey);
        }
        return resp;
    }

    public String getLongUrl(String shortCode) {
       return linkService.getLongUrl(shortCode);
    }
    public void deleteLink(String shortCode) {
        linkService.deleteLink(shortCode);
    }
}
