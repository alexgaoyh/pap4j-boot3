package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilTest {

    @Test
    public void indexOf2Test() {
        String str1 = "扫地僧\uD85D\uDC64一个扫地僧";
        String str2 = "一个";

        int indexOf2 = StringUtil.indexOf2(str1, str2);
        int indexOf = str1.indexOf(str2);
        assertEquals(indexOf2, 4);
        assertEquals(indexOf, 5);

    }

}
