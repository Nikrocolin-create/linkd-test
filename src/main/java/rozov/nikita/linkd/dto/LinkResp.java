package rozov.nikita.linkd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class LinkResp {
    private String shortCode;
    private String shortUrl;
    private Instant expiresAt;
}

