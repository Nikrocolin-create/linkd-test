package rozov.nikita.linkd.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
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
public class LinkController {
    private final LinkService service;

    @PostMapping("/links")
    public ResponseEntity<LinkResp> createLink(@RequestBody @Valid CreateLinkReq req) {
        LinkResp response = service.create(req);
        return ResponseEntity.created(URI.create(response.getShortUrl())).body(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> resolve(@PathVariable String code) {
        String longUrl = service.getLongUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}
