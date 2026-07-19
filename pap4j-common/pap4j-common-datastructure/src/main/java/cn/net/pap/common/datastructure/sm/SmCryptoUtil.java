package cn.net.pap.common.datastructure.sm;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * <p><strong>SmCryptoUtil</strong> 提供了国密 (Guomi) 算法 (SM2, SM3, SM4) 的实现工具类。</p>
 * <p>适合初学者理解的国密算法科普与使用指南：</p>
 * 
 * <h3>1. 什么是国密算法？</h3>
 * <ul>
 *     <li><strong>SM3 (密码杂凑算法)</strong>：相当于国密版的 MD5 或 SHA-256。它是单向的，也就是说只能从“明文”算出“哈希值”，无法反向解密。常用于密码存储（加盐哈希）和数据防篡改校验。</li>
 *     <li><strong>SM4 (对称加密算法)</strong>：相当于国密版的 AES。加密和解密使用“同一个密钥”。速度非常快，适合给大段文本或文件加密。
 *         <ul>
 *             <li><em>ECB 模式</em>：电子密码本模式。相同的明文块会被加密成相同的密文块，不安全，但不需要初始向量 (IV)。</li>
 *             <li><em>CBC 模式</em>：密码分组链接模式。引入了初始向量 (IV)，前一个分组的密文会与当前分组的明文异或后再加密。极其安全，推荐使用。</li>
 *         </ul>
 *     </li>
 *     <li><strong>SM2 (非对称加密算法)</strong>：相当于国密版的 RSA/ECC。它有一对密钥：公钥和私钥。
 *         <ul>
 *             <li><em>加密与解密</em>：公钥公开，任何人都可以用<strong>公钥加密</strong>数据，但只有持有对应的<strong>私钥的人才能解密</strong>。</li>
 *             <li><em>签名与验签</em>：私钥保密，私钥持有者可以用<strong>私钥签名</strong>，其他人可以用<strong>公钥验签</strong>，确保数据确实来自私钥持有者且未被篡改。</li>
 *         </ul>
 *     </li>
 * </ul>
 * 
 * <h3>2. 为什么需要 Hex (十六进制) 转换？</h3>
 * <p>加密算法处理的都是二进制的字节数组 (byte[])，这些二进制数据直接转成字符串会显示成乱码。
 * 为了方便在网络传输、数据库存储以及代码里直观显示，我们使用十六进制字符串 (Hex，即 0-9 和 a-f) 来展示这些二进制数据。</p>
 */
public class SmCryptoUtil {

    // 十六进制字符表，用于将字节快速转为可读字符
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    // 静态代码块：当类被加载时，自动向 Java 安全管理器注册 Bouncy Castle (BC) 加密库
    // 只有注册了 BC 提供者，Java 底层才能识别 "SM2", "SM3", "SM4" 算法名称
    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 将字节数组 (byte[]) 转换为十六进制字符串 (Hex String)。
     * <p>例如：字节数组 {10, 15} 会转为十六进制字符串 "0abf"。</p>
     *
     * @param bytes 原始二进制字节数组
     * @return 转换后的十六进制小写字符串
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF; // 取出字节的 8 位无符号整数值
            hexChars[i * 2] = HEX_CHARS[v >>> 4]; // 获取高 4 位的十六进制字符
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F]; // 获取低 4 位的十六进制字符
        }
        return new String(hexChars);
    }

    /**
     * 将十六进制字符串 (Hex String) 还原为字节数组 (byte[])。
     * <p>这是 toHex 方法的反向操作，常用于把网络传输来的密钥/密文字符串转回字节处理。</p>
     *
     * @param hex 十六进制字符串 (如 "0abf")
     * @return 还原后的二进制字节数组
     */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            return null;
        }
        int len = hex.length();
        byte[] data = new byte[len / 2]; // 两个十六进制字符代表一个字节
        for (int i = 0; i < len; i += 2) {
            // 将相邻的两个字符解析为对应的 8 位字节值
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 计算 SM3 密码杂凑值 (也就是哈希值/消息摘要)。
     * <p>输入任意长度的数据，输出固定 32 字节 (256 位) 的防篡改指纹。</p>
     *
     * @param data 原始数据的二进制字节数组
     * @return 32字节长度的哈希值
     * @throws RuntimeException 如果底层不支持 SM3 算法
     */
    public static byte[] sm3(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data to hash cannot be null");
        }
        try {
            // 获取 JDK 的消息摘要引擎实例，并指定使用注册好的 BC 库中的 SM3 算法
            MessageDigest digest = MessageDigest.getInstance("SM3", "BC");
            return digest.digest(data);
        } catch (Exception e) {
            throw new RuntimeException("SM3 hash execution failed", e);
        }
    }

    /**
     * 计算 SM3 杂凑值，并将结果转为 64 个字符长度的十六进制字符串。
     * <p>提示：SM3 产生的哈希值在二进制层面是 256 位的（即 32 字节），由于一个字节转成十六进制是 2 个字符，所以表现为 64 个字符的十六进制字符串。</p>
     *
     * @param data 原始数据的二进制字节数组
     * @return 64个字符长度的十六进制哈希指纹字符串
     */
    public static String sm3Hex(byte[] data) {
        return toHex(sm3(data));
    }

    /**
     * 计算普通文本字符串的 SM3 杂凑值，并将结果转为 64 个字符长度的十六进制字符串。
     * <p>提示：SM3 产生的哈希值在二进制层面是 256 位的（即 32 字节），由于一个字节转成十六进制是 2 个字符，所以表现为 64 个字符的十六进制字符串。</p>
     * <p>注意：我们会先将字符串按 UTF-8 编码转化为字节数组，然后计算 SM3。</p>
     *
     * @param text 原始明文字符串
     * @return 64个字符长度的十六进制哈希指纹字符串
     */
    public static String sm3Hex(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text to hash cannot be null");
        }
        return sm3Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SM4 对称加密：ECB 模式加密。
     * <p>注意：ECB 模式安全性较低，不建议在安全性要求极高的业务场景下使用。本方法使用 PKCS7Padding 填充方式。</p>
     *
     * @param data 待加密的明文字节数组
     * @param key  16字节的对称密钥 (必须是 128 位，即 16 个 byte)
     * @return 加密后的密文字节数组
     */
    public static byte[] sm4EncryptEcb(byte[] data, byte[] key) {
        if (data == null) {
            throw new IllegalArgumentException("Data to encrypt cannot be null");
        }
        validateSm4Key(key); // 校验密钥长度是否符合 SM4 标准的 16 字节
        try {
            // 获取 SM4 算法 ECB 分组模式、PKCS7 填充的密码机实例
            Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS7Padding", "BC");
            // 初始化密码机为“加密模式”，并包装密钥
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"));
            return cipher.doFinal(data); // 执行加密计算
        } catch (Exception e) {
            throw new RuntimeException("SM4 ECB encryption failed", e);
        }
    }

    /**
     * SM4 对称解密：ECB 模式解密。
     *
     * @param data 密文字节数组
     * @param key  与加密时相同的 16 字节对称密钥
     * @return 解密后的原始明文字节数组
     */
    public static byte[] sm4DecryptEcb(byte[] data, byte[] key) {
        if (data == null) {
            throw new IllegalArgumentException("Data to decrypt cannot be null");
        }
        validateSm4Key(key);
        try {
            Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS7Padding", "BC");
            // 初始化密码机为“解密模式”，并包装密钥
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"));
            return cipher.doFinal(data); // 执行解密计算并自动去除填充
        } catch (Exception e) {
            throw new RuntimeException("SM4 ECB decryption failed", e);
        }
    }

    /**
     * SM4 对称加密：CBC 模式加密 (推荐使用)。
     * <p>CBC 模式每次加密都需要一个随机且不重复的初始向量 (IV) 来增加密文随机性。</p>
     *
     * @param data 待加密的明文字节数组
     * @param key  16字节对称密钥 (128位)
     * @param iv   16字节初始向量 (128位，每次加密可以生成随机字节，解密时需传入相同的值)
     * @return 加密后的密文字节数组
     */
    public static byte[] sm4EncryptCbc(byte[] data, byte[] key, byte[] iv) {
        if (data == null) {
            throw new IllegalArgumentException("Data to encrypt cannot be null");
        }
        validateSm4Key(key);
        validateSm4Iv(iv); // 校验向量长度是否符合 16 字节
        try {
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", "BC");
            // 初始化密码机为“加密模式”，包装密钥和初始向量 Spec
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("SM4 CBC encryption failed", e);
        }
    }

    /**
     * SM4 对称解密：CBC 模式解密。
     *
     * @param data 密文字节数组
     * @param key  与加密相同的 16 字节对称密钥
     * @param iv   与加密相同的 16 字节初始向量 (IV)
     * @return 解密后的原始明文字节数组
     */
    public static byte[] sm4DecryptCbc(byte[] data, byte[] key, byte[] iv) {
        if (data == null) {
            throw new IllegalArgumentException("Data to decrypt cannot be null");
        }
        validateSm4Key(key);
        validateSm4Iv(iv);
        try {
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", "BC");
            // 初始化密码机为“解密模式”，并传入对应的密钥与 IV
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("SM4 CBC decryption failed", e);
        }
    }

    /**
     * SM4 ECB 模式加密 (方便业务使用的 String 包装版本)。
     *
     * @param data   需要加密的明文字符串
     * @param keyHex 16字节对称密钥的十六进制字符串表示 (长度必须为 32)
     * @return 加密后的密文的十六进制字符串表示
     */
    public static String sm4EncryptEcbHex(String data, String keyHex) {
        if (data == null) {
            throw new IllegalArgumentException("Data to encrypt cannot be null");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8); // 字符串按 UTF-8 转字节
        byte[] keyBytes = fromHex(keyHex); // 密钥 Hex 转字节
        byte[] cipherBytes = sm4EncryptEcb(dataBytes, keyBytes); // 加密
        return toHex(cipherBytes); // 密文转 Hex 字符串输出
    }

    /**
     * SM4 ECB 模式解密 (方便业务使用的 String 包装版本)。
     *
     * @param cipherHex 加密产生的密文十六进制字符串
     * @param keyHex    加密时所用的 16 字节对称密钥的十六进制字符串表示
     * @return 解密还原出的明文字符串
     */
    public static String sm4DecryptEcbHex(String cipherHex, String keyHex) {
        byte[] cipherBytes = fromHex(cipherHex); // 密文 Hex 转字节
        byte[] keyBytes = fromHex(keyHex); // 密钥 Hex 转字节
        byte[] plainBytes = sm4DecryptEcb(cipherBytes, keyBytes); // 解密
        return new String(plainBytes, StandardCharsets.UTF_8); // 字节以 UTF-8 还原明文字符串
    }

    /**
     * SM4 CBC 模式加密 (方便业务使用的 String 包装版本)。
     *
     * @param data   需要加密的明文字符串
     * @param keyHex 16字节对称密钥的十六进制字符串表示 (长度为 32)
     * @param ivHex  16字节初始向量的十六进制字符串表示 (长度为 32)
     * @return 加密后的密文的十六进制字符串表示
     */
    public static String sm4EncryptCbcHex(String data, String keyHex, String ivHex) {
        if (data == null) {
            throw new IllegalArgumentException("Data to encrypt cannot be null");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = fromHex(keyHex);
        byte[] ivBytes = fromHex(ivHex);
        byte[] cipherBytes = sm4EncryptCbc(dataBytes, keyBytes, ivBytes);
        return toHex(cipherBytes);
    }

    /**
     * SM4 CBC 模式解密 (方便业务使用的 String 包装版本)。
     *
     * @param cipherHex 加密产生的密文十六进制字符串
     * @param keyHex    加密时所用的对称密钥的十六进制字符串表示
     * @param ivHex     加密时所用的初始向量的十六进制字符串表示
     * @return 解密还原出的明文字符串
     */
    public static String sm4DecryptCbcHex(String cipherHex, String keyHex, String ivHex) {
        byte[] cipherBytes = fromHex(cipherHex);
        byte[] keyBytes = fromHex(keyHex);
        byte[] ivBytes = fromHex(ivHex);
        byte[] plainBytes = sm4DecryptCbc(cipherBytes, keyBytes, ivBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    /**
     * 生成 SM2 密钥对 (包含一个公钥公用，和一个私钥私有)。
     * <p>底层使用椭圆曲线密码学 (ECC) 的国密标准曲线参数 "sm2p256v1"。</p>
     *
     * @return 包含公钥和私钥的 KeyPair 对象
     */
    public static KeyPair generateSm2KeyPair() {
        try {
            // 获取椭圆曲线 EC 的密钥生成器
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
            // 使用国密特定的 sm2p256v1 曲线参数初始化密钥生成器
            generator.initialize(new ECGenParameterSpec("sm2p256v1"));
            return generator.generateKeyPair(); // 产生密钥对
        } catch (Exception e) {
            throw new RuntimeException("SM2 key pair generation failed", e);
        }
    }

    /**
     * SM2 非对称加密：使用公钥加密数据。
     * <p>非对称加密特点：公钥加密出的数据，只能用对应的私钥解开。</p>
     *
     * @param data      原始待加密的二进制字节数组
     * @param publicKey 用于加密的 SM2 公钥
     * @return 加密后的密文字节数组
     */
    public static byte[] sm2Encrypt(byte[] data, PublicKey publicKey) {
        if (data == null || publicKey == null) {
            throw new IllegalArgumentException("Data and public key cannot be null");
        }
        try {
            Cipher cipher = Cipher.getInstance("SM2", "BC");
            // 初始化密码机为“加密模式”，传入公钥
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("SM2 encryption failed", e);
        }
    }

    /**
     * SM2 非对称解密：使用私钥解密数据。
     *
     * @param data       密文字节数组
     * @param privateKey 与加密公钥配对的 SM2 私钥
     * @return 解密后的原始明文字节数组
     */
    public static byte[] sm2Decrypt(byte[] data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("Data and private key cannot be null");
        }
        try {
            Cipher cipher = Cipher.getInstance("SM2", "BC");
            // 初始化密码机为“解密模式”，传入私钥
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("SM2 decryption failed", e);
        }
    }

    /**
     * SM2 非对称加密 (方便业务使用的 String 包装版本，使用公钥对象)。
     *
     * @param data      待加密的明文字符串
     * @param publicKey 用于加密的公钥对象
     * @return 密文的十六进制字符串表示
     */
    public static String sm2EncryptHex(String data, PublicKey publicKey) {
        if (data == null) {
            throw new IllegalArgumentException("Data to encrypt cannot be null");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] cipherBytes = sm2Encrypt(dataBytes, publicKey);
        return toHex(cipherBytes);
    }

    /**
     * SM2 非对称加密 (方便业务使用的 String 包装版本，公钥也是十六进制字符串传入)。
     *
     * @param data             待加密的明文字符串
     * @param publicKeyX509Hex X509标准格式编码的公钥的十六进制表示
     * @return 密文的十六进制字符串表示
     */
    public static String sm2EncryptHex(String data, String publicKeyX509Hex) {
        // 先利用十六进制字符串反推出 PublicKey 公钥对象，再加密
        PublicKey publicKey = generatePublicKeyFromX509(fromHex(publicKeyX509Hex));
        return sm2EncryptHex(data, publicKey);
    }

    /**
     * SM2 非对称解密 (方便业务使用的 String 包装版本，使用私钥对象)。
     *
     * @param cipherHex  密文的十六进制字符串表示
     * @param privateKey 用于解密的私钥对象
     * @return 解密还原出的明文字符串
     */
    public static String sm2DecryptHex(String cipherHex, PrivateKey privateKey) {
        byte[] cipherBytes = fromHex(cipherHex);
        byte[] plainBytes = sm2Decrypt(cipherBytes, privateKey);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    /**
     * SM2 非对称解密 (方便业务使用的 String 包装版本，私钥也是十六进制字符串传入)。
     *
     * @param cipherHex          密文的十六进制字符串表示
     * @param privateKeyPkcs8Hex PKCS8标准格式编码的私钥的十六进制表示
     * @return 解密还原出的明文字符串
     */
    public static String sm2DecryptHex(String cipherHex, String privateKeyPkcs8Hex) {
        // 先从十六进制私钥反推出 PrivateKey 私钥对象，再解密
        PrivateKey privateKey = generatePrivateKeyFromPkcs8(fromHex(privateKeyPkcs8Hex));
        return sm2DecryptHex(cipherHex, privateKey);
    }

    /**
     * SM2 签名：使用私钥为原始数据生成数字签名。
     * <p>采用标准的 "SM3withSM2" 签名算法（使用 SM3 计算摘要，再用 SM2 私钥对摘要进行签名加密）。</p>
     *
     * @param data       待签名原始数据的字节数组
     * @param privateKey SM2 私钥 (只有持有私钥方能签名)
     * @return 签名结果的二进制字节数组 (包含签名值 r 和 s 的 DER 编码)
     */
    public static byte[] sm2Sign(byte[] data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("Data and private key cannot be null");
        }
        try {
            // 获取签名生成与校验引擎实例
            Signature signature = Signature.getInstance("SM3withSM2", "BC");
            // 初始化为“签名模式”，传入私钥
            signature.initSign(privateKey);
            signature.update(data); // 载入要签名的数据
            return signature.sign(); // 计算并输出数字签名
        } catch (Exception e) {
            throw new RuntimeException("SM2 signing failed", e);
        }
    }

    /**
     * SM2 验签：使用公钥验证数字签名是否正确。
     * <p>验签通过可以证明两件事：1. 数据确实是持有配对私钥的人签名的；2. 数据在签名后未被篡改过。</p>
     *
     * @param data      原始明文数据字节数组
     * @param signBytes 待校验的数字签名字节数组
     * @param publicKey 发行签名者的 SM2 公钥
     * @return true 代表验签成功，签名合法且内容完整；false 代表签名无效或数据已被篡改
     */
    public static boolean sm2Verify(byte[] data, byte[] signBytes, PublicKey publicKey) {
        if (data == null || signBytes == null || publicKey == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        try {
            Signature signature = Signature.getInstance("SM3withSM2", "BC");
            // 初始化为“验签模式”，传入公钥
            signature.initVerify(publicKey);
            signature.update(data); // 载入收到的原始明文数据
            return signature.verify(signBytes); // 校验签名是否与数据契合
        } catch (Exception e) {
            throw new RuntimeException("SM2 verification failed", e);
        }
    }

    /**
     * SM2 签名 (方便业务使用的 String 包装版本，使用私钥对象)。
     *
     * @param data       原始字符串明文数据
     * @param privateKey 用于签名的私钥对象
     * @return 签名的十六进制字符串表示
     */
    public static String sm2SignHex(String data, PrivateKey privateKey) {
        if (data == null) {
            throw new IllegalArgumentException("Data to sign cannot be null");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signBytes = sm2Sign(dataBytes, privateKey);
        return toHex(signBytes);
    }

    /**
     * SM2 签名 (方便业务使用的 String 包装版本，私钥以十六进制字符串传入)。
     *
     * @param data               原始字符串明文数据
     * @param privateKeyPkcs8Hex PKCS8格式私钥的十六进制表示
     * @return 签名的十六进制字符串表示
     */
    public static String sm2SignHex(String data, String privateKeyPkcs8Hex) {
        PrivateKey privateKey = generatePrivateKeyFromPkcs8(fromHex(privateKeyPkcs8Hex));
        return sm2SignHex(data, privateKey);
    }

    /**
     * SM2 验签 (方便业务使用的 String 包装版本，使用公钥对象)。
     *
     * @param data         原始明文字符串
     * @param signatureHex 待检验的十六进制签名串
     * @param publicKey    用于验签的公钥对象
     * @return 是否验签成功
     */
    public static boolean sm2VerifyHex(String data, String signatureHex, PublicKey publicKey) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signBytes = fromHex(signatureHex);
        return sm2Verify(dataBytes, signBytes, publicKey);
    }

    /**
     * SM2 验签 (方便业务使用的 String 包装版本，公钥以十六进制字符串传入)。
     *
     * @param data             原始明文字符串
     * @param signatureHex     待检验的十六进制签名串
     * @param publicKeyX509Hex X509格式公钥的十六进制表示
     * @return 是否验签成功
     */
    public static boolean sm2VerifyHex(String data, String signatureHex, String publicKeyX509Hex) {
        PublicKey publicKey = generatePublicKeyFromX509(fromHex(publicKeyX509Hex));
        return sm2VerifyHex(data, signatureHex, publicKey);
    }

    /**
     * 从 PKCS8 编码的密钥二进制字节数组中恢复 SM2 的 PrivateKey (私钥) 对象。
     * <p>在 Java 中，密钥通常是用标准的 PKCS8 规范来编码并存储的，此方法用于反序列化。</p>
     *
     * @param pkcs8Bytes 遵循 PKCS8 编码标准表示私钥的字节数组
     * @return PrivateKey 密钥接口实例
     */
    public static PrivateKey generatePrivateKeyFromPkcs8(byte[] pkcs8Bytes) {
        if (pkcs8Bytes == null) {
            throw new IllegalArgumentException("PKCS8 bytes cannot be null");
        }
        try {
            // 获取椭圆曲线 EC 的密钥转换器，支持 EC 和 SM2 密钥规格
            KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
            // 根据 PKCS8 规约解码并还原出私钥实例
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to restore SM2 private key from PKCS8 bytes", e);
        }
    }

    /**
     * 从 X509 编码的密钥二进制字节数组中恢复 SM2 的 PublicKey (公钥) 对象。
     * <p>在 Java 中，公钥通常是用标准的 X509 规范编码并进行网络分发的，此方法用于反序列化。</p>
     *
     * @param x509Bytes 遵循 X509 编码标准表示公钥的字节数组
     * @return PublicKey 公钥接口实例
     */
    public static PublicKey generatePublicKeyFromX509(byte[] x509Bytes) {
        if (x509Bytes == null) {
            throw new IllegalArgumentException("X509 bytes cannot be null");
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
            // 根据 X509 规约解码并还原出公钥实例
            return keyFactory.generatePublic(new X509EncodedKeySpec(x509Bytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to restore SM2 public key from X509 bytes", e);
        }
    }

    // 辅助方法：确保传入的 SM4 对称密钥大小正好为 16 字节 (128位)。SM4 只能使用 128 位强度的密钥。
    private static void validateSm4Key(byte[] key) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 key must be exactly 16 bytes (128 bits)");
        }
    }

    // 辅助方法：确保传入的 CBC 初始向量大小正好为 16 字节 (128位)。
    private static void validateSm4Iv(byte[] iv) {
        if (iv == null || iv.length != 16) {
            throw new IllegalArgumentException("SM4 IV must be exactly 16 bytes (128 bits)");
        }
    }
}
