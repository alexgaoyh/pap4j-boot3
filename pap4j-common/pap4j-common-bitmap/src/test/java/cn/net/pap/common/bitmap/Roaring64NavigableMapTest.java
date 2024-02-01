package cn.net.pap.common.bitmap;

import org.junit.jupiter.api.Test;
import org.roaringbitmap.longlong.LongIterator;
import org.roaringbitmap.longlong.Roaring64NavigableMap;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Roaring64NavigableMapTest {
    @Test
    public void simpleTest() {
        long currentTimeMillis = System.currentTimeMillis();
        Roaring64NavigableMap r64nMap = new Roaring64NavigableMap();
        // 添加范围，前闭区间后开区间
        long rangeLong = 1000000l;
        r64nMap.add(currentTimeMillis, currentTimeMillis + rangeLong);
        // 取第K个值，并判断值是否正确。
        long value1000 = r64nMap.select(1000);
        assertTrue(currentTimeMillis == value1000 - 1000l);
        // 判断数据长度
        assertTrue(r64nMap.getLongCardinality() == rangeLong);
        // 获得所有值
        LongIterator longIterator = r64nMap.getLongIterator();
        while (longIterator.hasNext()){
            assertTrue(longIterator.next() == currentTimeMillis);
            break;
        }
        // 是否包含
        boolean containsBool = r64nMap.contains(currentTimeMillis);
        assertTrue(containsBool);

        // 序列化
        String serialize = Roaring64NavigableMapUtil.serialize(r64nMap);
        // 反序列化
        Roaring64NavigableMap dBitMap = Roaring64NavigableMapUtil.deserialize(serialize);
        // 经过序列化和反序列化之后，再次判断数据长度
        assertTrue(dBitMap.getLongCardinality() == rangeLong);
    }

}
