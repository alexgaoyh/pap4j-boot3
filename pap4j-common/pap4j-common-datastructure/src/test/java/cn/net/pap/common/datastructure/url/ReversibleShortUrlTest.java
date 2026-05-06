package cn.net.pap.common.datastructure.url;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReversibleShortUrlTest {

    private static final Logger log = LoggerFactory.getLogger(ReversibleShortUrlTest.class);

    @Test
    public void urlTest() {
        String url = "我是中文https://pap-docs.pap.net.cn,!234.乱七八糟的各种字符WQER@#$%^&*()+{:>}";
        String endocde = ReversibleShortUrl.encode(url);
        log.info("{}", endocde);
        String decode = ReversibleShortUrl.decode(endocde);
        assertTrue(decode.equals(url));
    }

    @Test
    public void urlTest2() {
        String url = "我是中文https://pap-docs.pap.net.cn,!234.乱七八糟的各种字符WQER@#$%^&*()+{:>}";
        String endocde = ReversibleShortUrl.encodeReverse(url);
        log.info("{}", endocde);
        String decode = ReversibleShortUrl.decodeReverse(endocde);
        assertTrue(decode.equals(url));
    }

}
