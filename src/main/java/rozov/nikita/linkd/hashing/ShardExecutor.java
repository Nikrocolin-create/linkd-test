package rozov.nikita.linkd.hashing;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

//@Service
@RequiredArgsConstructor
public class ShardExecutor {
    private final ConsistentHashRing ring;
    private final PlatformTransactionManager txManager;

    public <T> T onShardFor(String key, Supplier<T> work) throws NoSuchAlgorithmException {
        ShardContext.set(ring.route(key));
        try {
            TransactionTemplate tx = new TransactionTemplate(txManager);
            return tx.execute(status -> work.get());   // транзакция уже на нужном шарде
        } finally {
            ShardContext.clear();
        }
    }
    /*example usage:
    *User u = shardExecutor.onShardFor(userId, () -> userRepository.findById(userId).orElseThrow());
    * */
}
