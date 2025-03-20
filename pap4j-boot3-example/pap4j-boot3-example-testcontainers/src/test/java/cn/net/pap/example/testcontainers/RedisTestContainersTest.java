package cn.net.pap.example.testcontainers;

import com.redis.testcontainers.RedisContainer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.Test;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainersTest {

    // @Test
    public void redisTest() {
        RedisContainer redisContainer =
                new RedisContainer(DockerImageName.parse("redis:5.0.3-alpine")).withExposedPorts(6379);
        redisContainer.start();

        RedisClient client = RedisClient.create(redisContainer.getRedisURI());
        try (StatefulRedisConnection<String, String> connect = client.connect()) {
            RedisCommands<String, String> sync = connect.sync();
            System.out.println(sync.ping());
        }
    }

}
