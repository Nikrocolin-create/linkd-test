package rozov.nikita.linkd.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rozov.nikita.linkd.dto.CreateLinkReq;
import rozov.nikita.linkd.dto.LinkResp;
import rozov.nikita.linkd.service.LinkService;

@RestController("api/v1")
@AllArgsConstructor
public class LinkController {
    private final LinkService service;
    @PostMapping("/links")
    public LinkResp createLink(@RequestBody CreateLinkReq req) {
        return service.create(req);
    }

    @GetMapping("/{code}")
    public LinkResp getLink(@PathVariable String code) {
        return service.getLink(code);
    }
}
