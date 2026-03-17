package cn.net.pap.common.bitmap;

import cn.net.pap.common.bitmap.exception.Roaring64NavigableMapException;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * <p><strong>Roaring64NavigableMap 工具类</strong></p>
 * <p>提供针对 {@code Roaring64NavigableMap} 的序列化、反序列化以及分页操作的便捷方法。</p>
 * <ul>
 * <li>支持基于 Base64 编码的序列化及反序列化转换。</li>
 * <li>支持依据插入顺序进行正序与逆序的分页查询。</li>
 * </ul>
 *
 * @author alexgaoyh
 */
public class Roaring64NavigableMapUtil {

    /**
     * <p><strong>序列化 Roaring64NavigableMap 为 Base64 字符串</strong></p>
     * <ul>
     * <li>将传入的 {@code Roaring64NavigableMap} 对象序列化并转换为 Base64 字符串。</li>
     * <li>如果传入的 bitmap 为空，则抛出 {@code Roaring64NavigableMapException}。</li>
     * </ul>
     *
     * @param bitmap 待序列化的 {@code Roaring64NavigableMap} 实例
     * @return 经过 Base64 编码的序列化字符串
     * @throws Roaring64NavigableMapException 序列化失败或入参为空时抛出
     */
    public static String serialize(Roaring64NavigableMap bitmap) {
        if (bitmap == null) {
            throw new Roaring64NavigableMapException("Bitmap cannot be null");
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            bitmap.serialize(dos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new Roaring64NavigableMapException("Error occurred during serialization", e);
        }
    }

    /**
     * <p><strong>从 Base64 字符串反序列化为 Roaring64NavigableMap</strong></p>
     * <ul>
     * <li>通过解析传入的 Base64 字符串，将其恢复为 {@code Roaring64NavigableMap} 实例。</li>
     * <li>如果传入的字符串为空，则抛出 {@code Roaring64NavigableMapException}。</li>
     * </ul>
     *
     * @param encrypt 包含序列化数据的 Base64 编码字符串
     * @return 反序列化后的 {@code Roaring64NavigableMap} 实例
     * @throws Roaring64NavigableMapException 反序列化失败或入参为空时抛出
     */
    public static Roaring64NavigableMap deserialize(String encrypt) {
        if (encrypt == null) {
            throw new Roaring64NavigableMapException("Serialized string cannot be null");
        }
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(encrypt));
             DataInputStream dis = new DataInputStream(bis)) {
            bitmap.deserialize(dis);
            return bitmap;
        } catch (IOException e) {
            throw new Roaring64NavigableMapException("Error occurred during deserialization", e);
        }
    }

    /**
     * <p><strong>正序分页查询</strong></p>
     * <ul>
     * <li>分页 按照添加顺序</li>
     * <li>基于 {@code Roaring64NavigableMap}，通过指定的页码和每页大小进行正序分页查询。</li>
     * </ul>
     *
     * @param map 包含数据的 {@code Roaring64NavigableMap}
     * @param pageNumber 当前页码，从 1 开始
     * @param pageSize 每页包含的数据量
     * @return 属于当前页的数据列表，若无效参数或超出范围则返回空列表
     */
    public static List<Long> getPageOrderByAdded(Roaring64NavigableMap map, long pageNumber, long pageSize) {
        if (pageNumber < 1 || pageSize < 1) {
            return new ArrayList<>();
        }
        long startRank = (pageNumber - 1) * pageSize;
        long totalSize = map.getLongCardinality();

        // 把 if 条件改成判断 startRank 是否大于等于总数
        if (startRank >= totalSize) {
            return new ArrayList<>();
        }
        List<Long> page = new ArrayList<>(Integer.parseInt(pageSize + ""));
        // 给 endRank 加一个限制，不能超过最大索引 (totalSize - 1)
        long endRank = Math.min(startRank + pageSize - 1, totalSize - 1);

        for (long rank = startRank; rank <= endRank; rank++) {
            long value = map.select(rank);
            page.add(value);
        }

        return page;
    }

    /**
     * <p><strong>逆序分页查询</strong></p>
     * <ul>
     * <li>分页 按照添加顺序逆序</li>
     * <li>基于 {@code Roaring64NavigableMap}，通过指定的页码和每页大小进行逆序分页查询。</li>
     * </ul>
     *
     * @param map 包含数据的 {@code Roaring64NavigableMap}
     * @param pageNumber 当前页码，从 1 开始
     * @param pageSize 每页包含的数据量
     * @return 属于当前页的数据列表，若无效参数或超出范围则返回空列表
     */
    public static List<Long> getPageOrderByAddedReverse(Roaring64NavigableMap map, long pageNumber, long pageSize) {
        if (pageNumber < 1 || pageSize < 1) {
            return new ArrayList<>();
        }
        if((pageNumber - 1) * pageSize >= map.getLongCardinality()) {
            return new ArrayList<>();
        }
        List<Long> page = new ArrayList<>(Integer.parseInt(pageSize + ""));
        long totalSize = map.getLongCardinality();

        // 计算起始和结束排名
        long endRank = totalSize - (pageNumber - 1) * pageSize - 1;
        long startRank = endRank - pageSize + 1;

        // 确保排名在有效范围内
        startRank = Math.max(startRank, 0);
        endRank = Math.min(endRank, totalSize - 1);

        // 反向遍历
        for (long rank = endRank; rank >= startRank; rank--) {
            long value = map.select(rank);
            page.add(value);
        }

        return page;
    }

}
