package rozov.nikita.linkd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CreateLinkReq {
    private String url;
    private String customAlias;
    private Long ttl;
}

