package rozov.nikita.linkd.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.service.LinkService;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
@Slf4j
public class LinkController {
    private final LinkService service;

    @PostMapping("/links")
    public ResponseEntity<LinkResp> createLink(@RequestBody @Valid CreateLinkReq req,
                                               @RequestParam(value = "cacheWarming", required = false, defaultValue = "false") boolean cacheWarming) {
        log.info("Received request to create link for URL: {}", req.getUrl());
        LinkResp response = service.create(req, cacheWarming);
        return ResponseEntity.created(URI.create(response.getShortUrl())).body(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> resolve(@PathVariable String code) {
        log.info("Received request to resolve short code: {}", code);
        String longUrl = service.getLongUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteLink(@PathVariable String code) {
        log.info("Received request to delete short code: {}", code);
        service.deleteLink(code);
        return ResponseEntity.noContent().build();
    }
}
