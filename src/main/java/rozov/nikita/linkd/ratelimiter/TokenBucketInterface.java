package rozov.nikita.linkd.ratelimiter;

public interface TokenBucketInterface {
    void tryConsume(String key);
}
