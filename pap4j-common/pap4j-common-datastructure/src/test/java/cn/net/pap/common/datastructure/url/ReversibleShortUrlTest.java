package cn.net.pap.common.datastructure.url;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReversibleShortUrlTest {

    @Test
    public void urlTest() {
        String url = "我是中文https://pap-docs.pap.net.cn,!234.乱七八糟的各种字符WQER@#$%^&*()+{:>}";
        String endocde = ReversibleShortUrl.encode(url);
        System.out.println(endocde);
        String decode = ReversibleShortUrl.decode(endocde);
        assertTrue(decode.equals(url));
    }

}
