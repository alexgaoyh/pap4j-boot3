package cn.net.pap.cache;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.cache.CacheApplication.class})
@TestPropertySource("classpath:application.properties")
public class CacheableFieldTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void redisTemplateTest() {
        String response = redisTemplate.execute((RedisConnection connection) -> {
            return connection.ping();
        });
        if(response.equals("PONG")) {
            redisTemplate.opsForValue().set("test", "test");
            Boolean delete = redisTemplate.delete("test");
            System.out.println(delete);
        } else {
            System.out.println("redisTemplate.isExposeConnection() == false");
        }
    }

    //@Test
    public void get() {
        Object threadId = redisTemplate.opsForValue().get("Proguard::Proguard:id:1721376543523:extMap:threadId");
        Object timeswap = redisTemplate.opsForValue().get("Proguard::Proguard:id:1721376543523:extMap:timeswap");
        Object extList = redisTemplate.opsForValue().get("Proguard::Proguard:id:1721376543523:extList");
        assertTrue(threadId instanceof String);
        assertTrue(timeswap instanceof Long);
        assertTrue(extList instanceof ArrayList);
        System.out.println(threadId);
        System.out.println(timeswap);
        System.out.println(extList);
    }

}
