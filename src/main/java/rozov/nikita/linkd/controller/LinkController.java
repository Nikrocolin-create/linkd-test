package rozov.nikita.linkd.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.manager.LinkManager;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
@Slf4j
public class LinkController {
    private final LinkManager manager;

    @PostMapping("/links")
    public ResponseEntity<LinkResp> createLink(@RequestBody @Valid CreateLinkReq req,
                                               @RequestParam(value = "cacheWarming", required = false, defaultValue = "false") boolean cacheWarming,
                                               @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey){
        log.info("Received request to create link for URL: {}", req.getUrl());
        LinkResp response = manager.create(req, cacheWarming, idempotencyKey);
        return ResponseEntity.created(URI.create(response.getShortUrl())).body(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> resolve(@PathVariable String code) {
        log.info("Received request to resolve short code: {}", code);
        String longUrl = manager.getLongUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteLink(@PathVariable String code) {
        log.info("Received request to delete short code: {}", code);
        manager.deleteLink(code);
        return ResponseEntity.noContent().build();
    }
}
