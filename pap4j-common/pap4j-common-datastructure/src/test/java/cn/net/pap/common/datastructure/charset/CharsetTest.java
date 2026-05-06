package cn.net.pap.common.datastructure.charset;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.SortedMap;

public class CharsetTest {

    private static final Logger log = LoggerFactory.getLogger(CharsetTest.class);

    @Test
    public void utf8Test() {
        // JVM default charset
        Charset charset = Charset.defaultCharset();
        // assertTrue(charset.name().equals("UTF-8"));

        // show all charset
        SortedMap<String, Charset> stringCharsetSortedMap = Charset.availableCharsets();
        for (Map.Entry<String, Charset> entry : stringCharsetSortedMap.entrySet()) {
            log.info("{}: {}", entry.getKey(), entry.getValue());
        }

    }

}
