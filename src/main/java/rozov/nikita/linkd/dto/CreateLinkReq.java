package rozov.nikita.linkd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CreateLinkReq {
    public CreateLinkReq(String url, String customAlias) {
        this.url = url;
        this.customAlias = customAlias;
    }
    @NotBlank
    @URL
    private String url;
    @Length(max = 8)
    private String customAlias;
    private Long ttl;
}

