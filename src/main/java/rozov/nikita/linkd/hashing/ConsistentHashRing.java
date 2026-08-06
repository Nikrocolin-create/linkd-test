package rozov.nikita.linkd.hashing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;


public class ConsistentHashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private static final int VNODES = 128;

    public ConsistentHashRing(Collection<String> shardIds) throws NoSuchAlgorithmException {
        for (String shard : shardIds)
            for (int i = 0; i < VNODES; i++)
                ring.put(hash(shard + "#" + i), shard);
    }

    public String route(String key) throws NoSuchAlgorithmException {
        if (ring.isEmpty()) throw new IllegalStateException("empty ring");
        long h = hash(key);
        Map.Entry<Long, String> e = ring.ceilingEntry(h);   // по часовой стрелке
        return (e != null ? e : ring.firstEntry()).getValue(); // иначе — начало кольца
    }

    // стабильный 64→32-бит хэш, эквивалент ring_hash в Postgres
    private long hash(String s) throws NoSuchAlgorithmException {
        byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(UTF_8));
        return ((d[0]&0xFFL)<<24)|((d[1]&0xFFL)<<16)|((d[2]&0xFFL)<<8)|(d[3]&0xFFL);
    }
}