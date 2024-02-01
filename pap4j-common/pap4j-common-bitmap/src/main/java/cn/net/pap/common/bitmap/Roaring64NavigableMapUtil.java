package cn.net.pap.common.bitmap;

import cn.net.pap.common.bitmap.exception.Roaring64NavigableMapException;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.io.*;
import java.util.Base64;

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
}
