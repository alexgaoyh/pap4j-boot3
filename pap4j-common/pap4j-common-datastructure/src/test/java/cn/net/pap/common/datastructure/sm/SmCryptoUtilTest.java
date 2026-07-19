package cn.net.pap.common.datastructure.sm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p><strong>SmCryptoUtilTest</strong> 提供了对 SmCryptoUtil 的单元测试，验证 SM2、SM3 及 SM4 的正确性。</p>
 */
public class SmCryptoUtilTest {

    @Test
    public void testSm3Hash() {
        // 标准测试向量：SM3("abc") = 66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0
        String source = "abc";
        String expectedHash = "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0";

        String hashHex = SmCryptoUtil.sm3Hex(source);
        assertEquals(expectedHash, hashHex);

        byte[] hashBytes = SmCryptoUtil.sm3(source.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedHash, SmCryptoUtil.toHex(hashBytes));
    }

    @Test
    public void testSm4Ecb() {
        byte[] key = SmCryptoUtil.fromHex("0123456789abcdeffedcba9876543210");
        String plainText = "Hello Guomi SM4 ECB!";
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        byte[] cipherBytes = SmCryptoUtil.sm4EncryptEcb(plainBytes, key);
        assertNotNull(cipherBytes);

        byte[] decryptedBytes = SmCryptoUtil.sm4DecryptEcb(cipherBytes, key);
        assertEquals(plainText, new String(decryptedBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testSm4Cbc() {
        byte[] key = SmCryptoUtil.fromHex("0123456789abcdeffedcba9876543210");
        byte[] iv = SmCryptoUtil.fromHex("1032547698badcfeefcdab8967452301");
        String plainText = "Hello Guomi SM4 CBC with PKCS7Padding!";
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        byte[] cipherBytes = SmCryptoUtil.sm4EncryptCbc(plainBytes, key, iv);
        assertNotNull(cipherBytes);

        byte[] decryptedBytes = SmCryptoUtil.sm4DecryptCbc(cipherBytes, key, iv);
        assertEquals(plainText, new String(decryptedBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testSm2EncryptDecrypt() {
        KeyPair keyPair = SmCryptoUtil.generateSm2KeyPair();
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());

        String plainText = "SM2 Asymmetric Crypto Test Value";
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        // 使用公钥加密
        byte[] cipherBytes = SmCryptoUtil.sm2Encrypt(plainBytes, keyPair.getPublic());
        assertNotNull(cipherBytes);

        // 使用私钥解密
        byte[] decryptedBytes = SmCryptoUtil.sm2Decrypt(cipherBytes, keyPair.getPrivate());
        assertEquals(plainText, new String(decryptedBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testSm2SignVerify() {
        KeyPair keyPair = SmCryptoUtil.generateSm2KeyPair();
        String plainText = "SM2 Digital Signature Test Data";
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        // 签名
        byte[] signature = SmCryptoUtil.sm2Sign(plainBytes, keyPair.getPrivate());
        assertNotNull(signature);

        // 验签
        boolean verifyResult = SmCryptoUtil.sm2Verify(plainBytes, signature, keyPair.getPublic());
        assertTrue(verifyResult);

        // 篡改数据验签
        byte[] modifiedBytes = "SM2 Digital Signature Test Data modified".getBytes(StandardCharsets.UTF_8);
        boolean verifyModifiedResult = SmCryptoUtil.sm2Verify(modifiedBytes, signature, keyPair.getPublic());
        assertFalse(verifyModifiedResult);
    }

    @Test
    public void testSm2KeyRestore() {
        KeyPair originalKeyPair = SmCryptoUtil.generateSm2KeyPair();
        byte[] privateKeyEncoded = originalKeyPair.getPrivate().getEncoded();
        byte[] publicKeyEncoded = originalKeyPair.getPublic().getEncoded();

        // 从字节还原
        PrivateKey restoredPrivateKey = SmCryptoUtil.generatePrivateKeyFromPkcs8(privateKeyEncoded);
        PublicKey restoredPublicKey = SmCryptoUtil.generatePublicKeyFromX509(publicKeyEncoded);

        assertNotNull(restoredPrivateKey);
        assertNotNull(restoredPublicKey);

        // 使用还原后的密钥测试加密解密
        String plainText = "Restore Keys Validation";
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        byte[] cipherBytes = SmCryptoUtil.sm2Encrypt(plainBytes, restoredPublicKey);
        byte[] decryptedBytes = SmCryptoUtil.sm2Decrypt(cipherBytes, restoredPrivateKey);

        assertEquals(plainText, new String(decryptedBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testSm4EcbHex() {
        String keyHex = "0123456789abcdeffedcba9876543210";
        String plainText = "Hello Guomi SM4 ECB String API!";

        String cipherHex = SmCryptoUtil.sm4EncryptEcbHex(plainText, keyHex);
        assertNotNull(cipherHex);

        String decryptedText = SmCryptoUtil.sm4DecryptEcbHex(cipherHex, keyHex);
        assertEquals(plainText, decryptedText);
    }

    @Test
    public void testSm4CbcHex() {
        String keyHex = "0123456789abcdeffedcba9876543210";
        String ivHex = "1032547698badcfeefcdab8967452301";
        String plainText = "Hello Guomi SM4 CBC String API!";

        String cipherHex = SmCryptoUtil.sm4EncryptCbcHex(plainText, keyHex, ivHex);
        assertNotNull(cipherHex);

        String decryptedText = SmCryptoUtil.sm4DecryptCbcHex(cipherHex, keyHex, ivHex);
        assertEquals(plainText, decryptedText);
    }

    @Test
    public void testSm2EncryptDecryptHex() {
        KeyPair keyPair = SmCryptoUtil.generateSm2KeyPair();
        String pubHex = SmCryptoUtil.toHex(keyPair.getPublic().getEncoded());
        String privHex = SmCryptoUtil.toHex(keyPair.getPrivate().getEncoded());

        String plainText = "SM2 String Encryption Test Value";

        // 用 PublicKey/PrivateKey 实例测试
        String cipherHex1 = SmCryptoUtil.sm2EncryptHex(plainText, keyPair.getPublic());
        String decryptedText1 = SmCryptoUtil.sm2DecryptHex(cipherHex1, keyPair.getPrivate());
        assertEquals(plainText, decryptedText1);

        // 用 Hex 还原后的密钥测试
        String cipherHex2 = SmCryptoUtil.sm2EncryptHex(plainText, pubHex);
        String decryptedText2 = SmCryptoUtil.sm2DecryptHex(cipherHex2, privHex);
        assertEquals(plainText, decryptedText2);
    }

    @Test
    public void testSm2SignVerifyHex() {
        KeyPair keyPair = SmCryptoUtil.generateSm2KeyPair();
        String pubHex = SmCryptoUtil.toHex(keyPair.getPublic().getEncoded());
        String privHex = SmCryptoUtil.toHex(keyPair.getPrivate().getEncoded());

        String plainText = "SM2 String Signature Test Data";

        // 用 PublicKey/PrivateKey 实例测试
        String signatureHex1 = SmCryptoUtil.sm2SignHex(plainText, keyPair.getPrivate());
        boolean verifyResult1 = SmCryptoUtil.sm2VerifyHex(plainText, signatureHex1, keyPair.getPublic());
        assertTrue(verifyResult1);

        // 用 Hex 还原后的密钥测试
        String signatureHex2 = SmCryptoUtil.sm2SignHex(plainText, privHex);
        boolean verifyResult2 = SmCryptoUtil.sm2VerifyHex(plainText, signatureHex2, pubHex);
        assertTrue(verifyResult2);

        // 篡改数据验签
        boolean verifyModified = SmCryptoUtil.sm2VerifyHex("modified data", signatureHex2, pubHex);
        assertFalse(verifyModified);
    }
}
