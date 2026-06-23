package rozov.nikita.linkd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CreateLinkReq {
    @NotBlank
    @URL
    private String url;
    //private String customAlias;
    private Long ttl;
}

