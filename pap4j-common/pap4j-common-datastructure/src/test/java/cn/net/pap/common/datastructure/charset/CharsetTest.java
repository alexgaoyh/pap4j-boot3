package cn.net.pap.common.datastructure.charset;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CharsetTest {

    @Test
    public void utf8Test() {
        // JVM default charset
        Charset charset = Charset.defaultCharset();
        assertTrue(charset.name().equals("UTF-8"));

        // show all charset
        SortedMap<String, Charset> stringCharsetSortedMap = Charset.availableCharsets();
        for(Map.Entry<String, Charset> entry : stringCharsetSortedMap.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

    }

}
