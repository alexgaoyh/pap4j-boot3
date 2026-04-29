package cn.net.pap.cache;

import cn.net.pap.common.bitmap.Roaring64NavigableMapUtil;
import org.junit.jupiter.api.Test;
import org.roaringbitmap.longlong.Roaring64NavigableMap;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {cn.net.pap.cache.CacheApplication.class})
@TestPropertySource("classpath:application.properties")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class RoaringBitmapRedisTest extends CacheBaseTest {

    private final RedisTemplate<String, Object> redisTemplate;

    public RoaringBitmapRedisTest(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Test
    public void redisTemplateTest() {
        long currentTimeMillis = System.currentTimeMillis();
        Roaring64NavigableMap r64nMap = new Roaring64NavigableMap();
        // 添加范围，前闭区间后开区间
        long rangeLong = 1000000l;
        r64nMap.add(currentTimeMillis, currentTimeMillis + rangeLong);

        redisTemplate.opsForValue().set("r64nMap", Roaring64NavigableMapUtil.serialize(r64nMap));

        Object serialize = redisTemplate.opsForValue().get("r64nMap");
        Roaring64NavigableMap dBitMap = Roaring64NavigableMapUtil.deserialize(serialize.toString());

        // 经过序列化和反序列化之后，再次判断数据长度
        assertTrue(dBitMap.getLongCardinality() == rangeLong);
    }

}
