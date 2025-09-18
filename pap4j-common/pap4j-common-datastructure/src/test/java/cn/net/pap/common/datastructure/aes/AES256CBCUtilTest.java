package cn.net.pap.common.datastructure.aes;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256-CBC 加密/解密
 */
public class AES256CBCUtilTest {

    // AES 配置（和 Lua 保持一致）
    private static final String KEY = "1234567890abcdef1234567890abcdef"; // 32字节 = 256位
    private static final String IV = "abcdef1357924680";                  // 16字节

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /**
     * AES-256-CBC 加密
     *
     * @param plainText 明文
     * @return Base64 编码后的密文
     */
    public static String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES-256-CBC 解密
     *
     * @param base64CipherText Base64 编码的密文
     * @return 明文
     */
    public static String decrypt(String base64CipherText) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decodedBytes = Base64.getDecoder().decode(base64CipherText);
        byte[] decrypted = cipher.doFinal(decodedBytes);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @Test
    public void test1() throws Exception {
        String text = """
                SELECT dd.DICT_ID, dd.DICT_CODE, dd.DICT_NAME,
                    JSON_ARRAYAGG(
                        JSON_OBJECT(
                            'DICT__DETAIL_ID', ddd.DICT__DETAIL_ID,
                            'DICT__DETAIL_CODE', ddd.DICT__DETAIL_CODE,
                            'DICT__DETAIL_NAME', ddd.DICT__DETAIL_NAME
                        )
                    ) AS details
                FROM t_data_dict dd
                LEFT JOIN t_data_dict_detail ddd ON dd.DICT_ID = ddd.DICT_ID
                GROUP BY dd.DICT_ID, dd.DICT_CODE, dd.DICT_NAME;
                """;
        text = text.replace("\n", " ");
        String enc = encrypt(text);
        String dec = decrypt(enc);
        System.out.println("原文: " + text);
        System.out.println("加密: " + enc);
        System.out.println("解密: " + dec);
    }

}
