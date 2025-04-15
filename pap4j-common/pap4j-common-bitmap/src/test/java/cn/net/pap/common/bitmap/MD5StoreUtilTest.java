package cn.net.pap.common.bitmap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

class MD5StoreUtilTest {

    @BeforeEach
    void setUp() {
        MD5StoreUtil.clear();
    }

    @Test
    void testAddAndContains() {
        String md5 = "d41d8cd98f00b204e9800998ecf8427e";

        MD5StoreUtil.add(md5);
        assertTrue(MD5StoreUtil.contains(md5));

        MD5StoreUtil.remove(md5);
        assertFalse(MD5StoreUtil.contains(md5));
    }

    @Test
    void testAddWithInvalidMD5() {
        assertThrows(IllegalArgumentException.class, () -> {
            MD5StoreUtil.add("invalidmd5");
        });
    }

    @Test
    void testContainsWithInvalidMD5() {
        assertFalse(MD5StoreUtil.contains("invalidmd5"));
    }

    @Test
    void testRemoveWithInvalidMD5() {
        MD5StoreUtil.remove("invalidmd5");
    }

    @Test
    void testSizeAndIsEmpty() {
        assertTrue(MD5StoreUtil.isEmpty());
        assertEquals(0, MD5StoreUtil.size());

        String md5 = "d41d8cd98f00b204e9800998ecf8427e";
        MD5StoreUtil.add(md5);

        assertFalse(MD5StoreUtil.isEmpty());
        assertEquals(1, MD5StoreUtil.size());
    }

    @Test
    void testClear() {
        String md5 = "d41d8cd98f00b204e9800998ecf8427e";
        MD5StoreUtil.add(md5);

        assertFalse(MD5StoreUtil.isEmpty());

        MD5StoreUtil.clear();

        assertTrue(MD5StoreUtil.isEmpty());
    }

    @Test
    void testAddAllAndContainsAll() {
        String md5_1 = "d41d8cd98f00b204e9800998ecf8427e";
        String md5_2 = "e2fc714c4727ee9395f324cd2e7f331f";

        MD5StoreUtil.addAll(Arrays.asList(md5_1, md5_2));

        assertTrue(MD5StoreUtil.containsAll(Arrays.asList(md5_1, md5_2)));
        assertFalse(MD5StoreUtil.containsAll(Arrays.asList(md5_1, "invalidmd5")));
    }

    @Test
    void testIterator() {
        String md5_1 = "d41d8cd98f00b204e9800998ecf8427e";
        String md5_2 = "e2fc714c4727ee9395f324cd2e7f331f";

        MD5StoreUtil.addAll(Arrays.asList(md5_1, md5_2));

        Iterator<String> iterator = MD5StoreUtil.iterator();
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
    }

    @Test
    void testEstimatedMemoryUsage() {
        String md5 = "d41d8cd98f00b204e9800998ecf8427e";
        MD5StoreUtil.add(md5);

        long memoryUsageAfterAdd = MD5StoreUtil.estimatedMemoryUsage();
        assertTrue(memoryUsageAfterAdd > 0);
    }

    @Test
    void testToMD5Hex() {
        long high64 = 0xabcdefabcdefabcdL;
        long low64 = 0x1234567890abcdefL;

        String md5Hex = MD5StoreUtil.toMD5Hex(high64, low64);
        assertEquals("abcdefabcdefabcd1234567890abcdef", md5Hex);
    }

    @Test
    void testAddAllWithInvalidMD5() {
        String md5_1 = "d41d8cd98f00b204e9800998ecf8427e";
        String invalidMd5 = "invalidmd5";
        MD5StoreUtil.addAll(Arrays.asList(md5_1, invalidMd5));

        assertTrue(MD5StoreUtil.contains(md5_1));
        assertFalse(MD5StoreUtil.contains(invalidMd5));
    }
}
