package rozov.nikita.linkd.exception;

public class IdempotencyTimeoutException extends RuntimeException {
    public IdempotencyTimeoutException(String s) {
        super(s);
    }
}
