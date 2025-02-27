package cn.net.pap.common.datastructure.url;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ReversibleShortUrl {

    private static final Base64.Encoder b64Encoder = Base64.getUrlEncoder();

    private static final Base64.Decoder b64Decoder = Base64.getUrlDecoder();

    /**
     * 编码
     *
     * @param url
     * @return
     */
    public static String encode(String url) {
        String compressed = b64Encoder.encodeToString(url.getBytes(StandardCharsets.UTF_8));
        return compressed;
    }

    /**
     * 解码
     *
     * @param shortUrl
     * @return
     */
    public static String decode(String shortUrl) {
        return new String(b64Decoder.decode(shortUrl), StandardCharsets.UTF_8);
    }

}
