package cn.net.pap.common.bitmap;

import cn.net.pap.common.bitmap.exception.Roaring64NavigableMapException;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Roaring64NavigableMapUtil {

    public static String serialize(Roaring64NavigableMap bitmap) {
        if (bitmap == null) {
            throw new Roaring64NavigableMapException("Bitmap cannot be null");
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            bitmap.serialize(dos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new Roaring64NavigableMapException("Error occurred during serialization: " + e.getMessage());
        }
    }

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
            throw new Roaring64NavigableMapException("Error occurred during deserialization: " + e.getMessage());
        }
    }

    /**
     * 分页 按照添加顺序
     * @param map
     * @param pageNumber
     * @param pageSize
     * @return
     */
    public static List<Long> getPageOrderByAdded(Roaring64NavigableMap map, long pageNumber, long pageSize) {
        if(pageNumber * pageSize > map.getLongCardinality()) {
            return new ArrayList<>();
        }
        List<Long> page = new ArrayList<>(Integer.parseInt(pageSize + ""));
        long startRank = (pageNumber - 1) * pageSize;
        long endRank = startRank + pageSize - 1;

        for (long rank = startRank; rank <= endRank; rank++) {
            long value = map.select(rank);
            page.add(value);
        }

        return page;
    }

    /**
     * 分页 按照添加顺序逆序
     * @param map
     * @param pageNumber
     * @param pageSize
     * @return
     */
    public static List<Long> getPageOrderByAddedReverse(Roaring64NavigableMap map, long pageNumber, long pageSize) {
        if(pageNumber * pageSize > map.getLongCardinality()) {
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
