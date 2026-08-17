package rozov.nikita.linkd.utility;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

public class SafeUrlValidator implements ConstraintValidator<SafeUrl, String> {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) return true; // за null отвечает @NotBlank

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return fail(ctx, "Malformed URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            return fail(ctx, "Only http and https are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return fail(ctx, "URL must contain a host");
        }
        // user:pass@host — частый обход фильтров, а заодно утечка креденшелов
        if (uri.getUserInfo() != null) {
            return fail(ctx, "Credentials in URL are not allowed");
        }

        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isPrivate(addr)) {
                    return fail(ctx, "URL points to a private network address");
                }
            }
        } catch (UnknownHostException e) {
            return fail(ctx, "Host cannot be resolved");
        }
        return true;
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress()      // 0.0.0.0, ::
                || a.isLoopbackAddress()   // 127.0.0.0/8, ::1
                || a.isLinkLocalAddress()  // 169.254.0.0/16 — сюда же метадата облаков
                || a.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF, second = b[1] & 0xFF;
            if (first == 100 && second >= 64 && second <= 127) return true; // CGNAT 100.64/10
            if (first == 192 && second == 0) return true;                   // 192.0.0.0/24
            if (first >= 240) return true;                                  // reserved
        } else if (b.length == 16) {
            if ((b[0] & 0xFE) == 0xFC) return true; // unique local fc00::/7
        }
        return false;
    }

    private boolean fail(ConstraintValidatorContext ctx, String msg) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
        return false;
    }
}
