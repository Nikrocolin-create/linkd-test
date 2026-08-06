package rozov.nikita.linkd.hashing;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//@Configuration
public class ShardingConfig {

    @Bean
    public DataSource dataSource() {
        Map<Object, Object> targets = new HashMap<>();
        targets.put("shard-1", buildDs("jdbc:postgresql://host1/db"));
        targets.put("shard-2", buildDs("jdbc:postgresql://host2/db"));
        targets.put("shard-3", buildDs("jdbc:postgresql://host3/db"));

        ShardRoutingDataSource rds = new ShardRoutingDataSource();
        rds.setTargetDataSources(targets);
        rds.setDefaultTargetDataSource(targets.get("shard-1"));
        return rds;
    }

    private DataSource buildDs(String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        // user/password/pool...
        return ds;
    }

    @Bean
    public ConsistentHashRing ring() throws NoSuchAlgorithmException {
        return new ConsistentHashRing(List.of("shard-1","shard-2","shard-3"));
    }
}
