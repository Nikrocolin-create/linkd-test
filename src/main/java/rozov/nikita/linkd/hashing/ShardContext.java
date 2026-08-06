package rozov.nikita.linkd.hashing;

public final class ShardContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    public static void set(String shardId) { CURRENT.set(shardId); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
