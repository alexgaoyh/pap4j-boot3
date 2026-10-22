package cn.net.pap.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CollectionUtil 单元测试")
class CollectionUtilTest {

    @Test
    @DisplayName("测试 fillNullKeys 连续键填充")
    void testFillNullKeys() {
        List<Map<Integer, String>> list = new ArrayList<>();

        Map<Integer, String> m1 = new HashMap<>();
        m1.put(null, "Row 1 without key");
        list.add(m1);

        Map<Integer, String> m2 = new HashMap<>();
        m2.put(null, "Row 2 without key");
        list.add(m2);

        Map<Integer, String> m3 = new HashMap<>();
        m3.put(100, "Header 100");
        list.add(m3);

        CollectionUtil.fillNullKeys(list);

        assertEquals("Row 1 without key", list.get(0).get(100));
        assertEquals("Row 2 without key", list.get(1).get(100));
        assertEquals("Header 100", list.get(2).get(100));
    }

    @Test
    @DisplayName("测试边界条件")
    void testEdgeCases() {
        assertDoesNotThrow(() -> CollectionUtil.fillNullKeys(null));
        assertDoesNotThrow(() -> CollectionUtil.fillNullKeys(Collections.emptyList()));
    }
}
