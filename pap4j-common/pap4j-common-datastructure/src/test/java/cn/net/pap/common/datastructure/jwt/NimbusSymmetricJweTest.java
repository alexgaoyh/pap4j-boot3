package cn.net.pap.common.datastructure.jwt;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>
 *
 * </p>
 *
 * <p>
 * 基于 nimbus-jose-jwt 10.8 的单元测试 简单的对称加密 JWE (Direct Encryption)。以下是关于WT 与 JWE (JSON Web Encryption)这两种标准的核心区别：
 * </p>
 * <ul>
 * <li>
 * <strong>普通 JWT (通常指 JWS - JSON Web Signature)：</strong><br>
 * 我们平时最常见的 JWT 是三段式的（<code>Header.Payload.Signature</code>）。<br>
 * 它的 Payload 仅仅是做了 Base64 编码，<strong>并没有加密</strong>。
 * 这意味着任何人拿到 Token 都能轻易解码并看到里面的明文数据（比如 user_id, email 等）。
 * 它只能保证数据不被篡改，不能保证数据不被偷看。
 * </li>
 * <li>
 * <strong>JWE (JSON Web Encryption)：</strong><br>
 * JWE 是 JWT 家族的另一个标准（<a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC 7516</a>）。<br>
 * 它将 Payload <strong>完全加密</strong>，生成的是一个五段式的字符串（<code>Header.EncryptedKey.IV.Ciphertext.AuthTag</code>）。
 * 只有掌握正确解密密钥的一方，才能看到里面的内容。
 * </li>
 * </ul>
 */
class NimbusSymmetricJweTest {

    /**
     * 在线解析： https://dinochiesa.github.io/jwt/
     * 左侧的 Encoded Token 输入 token
     * 右侧下部的 Direct Key (32 bytes, required: 32) 输入下面这个 32 位共享秘钥
     *
     * @throws Exception
     */
    @Test
    @DisplayName("测试：使用 32 字节密钥进行 DIR 和 A256GCM 对称加解密")
    void testSymmetricJwe() throws Exception {
        // 1. 准备 32 字节 (256 bits) 的共享密钥 注意：在 A256GCM 算法下，这里的长度必须严丝合缝是 32 个字符/字节
        byte[] secretKey = "12345678901234567890123456789012".getBytes();

        // 2. 准备 Payload (业务数据/声明)
        Date now = new Date();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("alexgaoyh").claim("email", "alexgaoyh@sina.com")
                .issueTime(now).expirationTime(new Date(now.getTime() + 1000 * 60 * 30))
                .build();

        // 3. 构建 JWE Header (指定直接加密 DIR 和 A256GCM 算法)
        JWEHeader header = new JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A256GCM);

        // 4. 将 Claims 转换为 Payload，并组装 JWEObject
        Payload payload = new Payload(claimsSet.toJSONObject());
        JWEObject jweObject = new JWEObject(header, payload);

        // 5. 执行加密操作
        jweObject.encrypt(new DirectEncrypter(secretKey));

        // 6. 序列化为最终的 Token 字符串
        String token = jweObject.serialize();

        System.out.println("====== 生成的 JWE Token ======");
        System.out.println(token + "\n");

        // --- 核心原理解密验证 ---
        // 验证 Token 格式是否为 5 段式 (Header.EncryptedKey.IV.Ciphertext.AuthTag)
        String[] parts = token.split("\\.");
        assertEquals(5, parts.length, "JWE Token 必须是由点分隔的 5 段式字符串");

        // 验证刚才讲过的原理：因为是 DIR 模式(直接使用共享密钥)，所以不需要传递加密后的密钥，第二段必定为空！
        assertTrue(parts[1].isEmpty(), "DIR 模式下 EncryptedKey (第二段) 必须为空");

        // ================= 解析与解密阶段 =================

        // 7. 解析 Token 字符串回 JWEObject 对象
        JWEObject parsedJweObject = JWEObject.parse(token);

        // 8. 执行解密操作 (使用相同的 32 字节密钥)
        parsedJweObject.decrypt(new DirectDecrypter(secretKey));

        // 9. 获取并验证解密后的 Claims
        JWTClaimsSet parsedClaimsSet = JWTClaimsSet.parse(parsedJweObject.getPayload().toJSONObject());

        assertEquals("alexgaoyh", parsedClaimsSet.getSubject());
        assertEquals("alexgaoyh@sina.com", parsedClaimsSet.getStringClaim("email"));

        System.out.println("====== 解密成功 ======");
        System.out.println("提取的 Subject: " + parsedClaimsSet.getSubject());
        System.out.println("提取的 Email: " + parsedClaimsSet.getStringClaim("email"));
    }

}
