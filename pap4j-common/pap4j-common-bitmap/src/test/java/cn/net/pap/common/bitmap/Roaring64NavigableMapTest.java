package cn.net.pap.common.bitmap;

import cn.net.pap.common.bitmap.exception.Roaring64NavigableMapException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.roaringbitmap.longlong.LongIterator;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.util.List;

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

    @Test
    public void getPageOrderByAddedTest() {
        long currentTimeMillis = 101l;
        Roaring64NavigableMap r64nMap = new Roaring64NavigableMap();
        // 添加范围，前闭区间后开区间
        long rangeLong = 100l;
        r64nMap.add(currentTimeMillis, currentTimeMillis + rangeLong);
        for(int pageIdx = 1; pageIdx <= 10; pageIdx++) {
            List<Long> valueList = Roaring64NavigableMapUtil.getPageOrderByAdded(r64nMap, pageIdx, 10);
            System.out.println(valueList);
        }

        System.out.println("----------------------------------------------");

        for(int pageIdx = 1; pageIdx <= 10; pageIdx++) {
            List<Long> valueList = Roaring64NavigableMapUtil.getPageOrderByAddedReverse(r64nMap, pageIdx, 10);
            System.out.println(valueList);
        }

    }

    @Test
    public void testSerialize_NullBitmap_ThrowsException() {
        // 测试 serialize 方法的 if (bitmap == null) 分支
        Roaring64NavigableMapException exception = Assertions.assertThrows(
                Roaring64NavigableMapException.class,
                () -> Roaring64NavigableMapUtil.serialize(null),
                "Expected serialize(null) to throw, but it didn't"
        );
        Assertions.assertEquals("Bitmap cannot be null", exception.getMessage());
    }

    @Test
    public void testDeserialize_NullEncrypt_ThrowsException() {
        // 测试 deserialize 方法的 if (encrypt == null) 分支
        Roaring64NavigableMapException exception = Assertions.assertThrows(
                Roaring64NavigableMapException.class,
                () -> Roaring64NavigableMapUtil.deserialize(null),
                "Expected deserialize(null) to throw, but it didn't"
        );
        Assertions.assertEquals("Serialized string cannot be null", exception.getMessage());
    }

    @Test
    public void testGetPageOrderByAdded_OutOfBounds_ReturnsEmptyList() {
        // 测试 getPageOrderByAdded 方法的 if(pageNumber * pageSize > map.getLongCardinality()) 分支
        Roaring64NavigableMap map = new Roaring64NavigableMap();
        map.add(1L);
        map.add(2L);
        map.add(3L); // 当前总容量为 3

        // 请求第 2 页，每页 2 条，2 * 2 = 4 > 3，触发 if 分支
        List<Long> result = Roaring64NavigableMapUtil.getPageOrderByAdded(map, 2, 2);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(!result.isEmpty(), "Result should be last page list");

        List<Long> result2 = Roaring64NavigableMapUtil.getPageOrderByAdded(map, 0, 0);
        Assertions.assertTrue(result2.isEmpty(), "Result should be empty");

        List<Long> result3 = Roaring64NavigableMapUtil.getPageOrderByAdded(map, 3, 2);
        Assertions.assertTrue(result3.isEmpty(), "Result should be empty");

    }

    @Test
    public void testGetPageOrderByAddedReverse_OutOfBounds_ReturnsEmptyList() {
        // 测试 getPageOrderByAddedReverse 方法的 if(pageNumber * pageSize > map.getLongCardinality()) 分支
        Roaring64NavigableMap map = new Roaring64NavigableMap();
        map.add(10L);
        map.add(20L); // 当前总容量为 2

        // 请求第 2 页，每页 2 条，2 * 2 = 4 > 2，触发 if 分支
        List<Long> result = Roaring64NavigableMapUtil.getPageOrderByAddedReverse(map, 2, 2);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty(), "Result should be an empty list");

        List<Long> result2 = Roaring64NavigableMapUtil.getPageOrderByAddedReverse(map, 0, 0);
        Assertions.assertTrue(result2.isEmpty(), "Result should be empty");

        List<Long> result3 = Roaring64NavigableMapUtil.getPageOrderByAddedReverse(map, 3, 2);
        Assertions.assertTrue(result3.isEmpty(), "Result should be empty");
    }

}
