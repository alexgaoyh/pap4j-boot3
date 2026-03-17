package cn.net.pap.common.datastructure.license;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p><strong>SimpleLicenseUtil</strong> 是一个用于生成和验证软件许可证的工具类。</p>
 *
 * <p>它使用 HMAC-SHA256 对许可证内容进行签名，并使用 Base64（URL 安全）进行编码。</p>
 *
 * <ul>
 *     <li>支持包含签发日期和过期日期的许可证生成。</li>
 *     <li>通过命令行参数验证传入的许可证。</li>
 *     <li>使用加密签名检查是否遭到篡改。</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * Map<String, String> props = new HashMap<>();
 * props.put("EXPIRY", "2025-12-31");
 * String licenseKey = SimpleLicenseUtil.generateSignedLicense(props);
 * boolean isValid = SimpleLicenseUtil.checkLicense(new String[]{"--license=" + licenseKey});
 * }</pre>
 */
public class SimpleLicenseUtil {

    /**
     * <p>无填充的 Base64 URL 安全编码器。</p>
     */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    
    /**
     * <p>Base64 URL 安全解码器。</p>
     */
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    /**
     * <p>许可证内容中属性之间的分隔符。</p>
     */
    private static final String PROPERTY_SEPARATOR = "|";
    
    /**
     * <p>许可证内容中键和值之间的分隔符。</p>
     */
    private static final String KEY_VALUE_SEPARATOR = ":";
    
    /**
     * <p>编码后的许可证内容与其签名之间的分隔符。</p>
     */
    private static final String CONTENT_SIGNATURE_SEPARATOR = "~";

    /**
     * <p>用于生成 HMAC 的密钥盐值。</p>
     */
    private static final String LICENSE_SALT = "pap.net.cn";
    
    /**
     * <p>用于解析和格式化许可证日期的日期格式化器。</p>
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * <p>检查提供的参数中是否包含有效的许可证。</p>
     * 
     * <p>它将查找以 <strong>--license=</strong> 开头的参数，并验证其值。</p>
     *
     * @param args 命令行参数。
     * @return 如果找到有效的许可证则返回 <strong>true</strong>；否则返回 <strong>false</strong>。
     */
    public static boolean checkLicense(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--license=")) {
                String licenseWithSignature = arg.substring("--license=".length());
                return validateLicenseKey(licenseWithSignature);
            }
        }
        return false;
    }

    /**
     * <p>验证给定的许可证密钥及其签名。</p>
     *
     * @param licenseWithSignature 包含签名的完整许可证字符串。
     * @return 如果许可证有效、签名匹配且未过期则返回 <strong>true</strong>；否则返回 <strong>false</strong>。
     */
    private static boolean validateLicenseKey(String licenseWithSignature) {
        try {
            // 1. 分离License内容和签名
            String[] parts = licenseWithSignature.split(CONTENT_SIGNATURE_SEPARATOR, 2);
            if (parts.length != 2) return false;

            String encodedContent = parts[0];
            String receivedSignature = parts[1];

            // 2. 解码License内容
            String licenseContent = new String(BASE64_DECODER.decode(encodedContent), StandardCharsets.UTF_8);

            // 3. 验证签名
            String computedSignature = calculateHMAC(encodedContent, LICENSE_SALT);
            if (!computedSignature.equals(receivedSignature)) {
                return false;
            }

            // 4. 解析License属性
            Map<String, String> licenseProps = parseLicenseContent(licenseContent);

            // 5. 检查必要属性
            if (!licenseProps.containsKey("EXPIRY") || !licenseProps.containsKey("ISSUEDATE")) {
                return false;
            }

            // 6. 验证日期
            return validateLicenseDates(licenseProps);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * <p>将解码后的许可证内容解析为键值对映射。</p>
     *
     * @param content 解码后的许可证内容。
     * @return 包含许可证属性的 {@link Map}。
     */
    private static Map<String, String> parseLicenseContent(String content) {
        Map<String, String> props = new HashMap<>();
        String[] pairs = content.split("\\" + PROPERTY_SEPARATOR);
        for (String pair : pairs) {
            String[] kv = pair.split(KEY_VALUE_SEPARATOR, 2);
            if (kv.length == 2) {
                props.put(kv[0], kv[1]);
            }
        }
        return props;
    }

    /**
     * <p>根据提供的属性生成带有签名的许可证字符串。</p>
     * 
     * <p>如果未提供，将自动添加当前日期作为 <strong>ISSUEDATE</strong>（签发日期）。</p>
     *
     * @param licenseProps 要包含在许可证中的属性。
     * @return 带有签名且已编码的许可证字符串。
     * @throws InvalidKeyException 如果 HMAC 密钥无效。
     * @throws NoSuchAlgorithmException 如果找不到 HMAC 算法。
     */
    public static String generateSignedLicense(Map<String, String> licenseProps) throws InvalidKeyException, NoSuchAlgorithmException {
        // 确保包含必要字段
        if (!licenseProps.containsKey("ISSUEDATE")) {
            licenseProps.put("ISSUEDATE", LocalDate.now().format(DATE_FORMATTER));
        }

        // 构建License内容字符串
        String licenseContent = licenseProps.entrySet().stream().map(entry -> entry.getKey() + KEY_VALUE_SEPARATOR + entry.getValue()).collect(Collectors.joining(PROPERTY_SEPARATOR));

        // Base64编码内容
        String encodedContent = BASE64_ENCODER.encodeToString(licenseContent.getBytes(StandardCharsets.UTF_8));

        // 计算签名
        String signature = calculateHMAC(encodedContent, LICENSE_SALT);

        return encodedContent + CONTENT_SIGNATURE_SEPARATOR + signature;
    }

    /**
     * <p>计算给定数据和盐值的 HMAC 签名。</p>
     *
     * @param data 要签名的数据。
     * @param salt 密钥盐值。
     * @return 经过 Base64 编码的 HMAC 签名。
     * @throws NoSuchAlgorithmException 如果算法不可用。
     * @throws InvalidKeyException 如果密钥无效。
     */
    private static String calculateHMAC(String data, String salt) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);

        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return BASE64_ENCODER.encodeToString(rawHmac);
    }

    /**
     * <p>验证许可证的签发日期和过期日期。</p>
     *
     * @param licenseProps 包含日期的许可证属性。
     * @return <strong>true</strong> 如果日期有效；否则返回 <strong>false</strong>。
     */
    private static boolean validateLicenseDates(Map<String, String> licenseProps) {
        try {
            LocalDate currentDate = LocalDate.now();
            LocalDate issueDate = LocalDate.parse(licenseProps.get("ISSUEDATE"), DATE_FORMATTER);
            LocalDate expiryDate = LocalDate.parse(licenseProps.get("EXPIRY"), DATE_FORMATTER);

            return !issueDate.isAfter(currentDate) && !currentDate.isAfter(expiryDate);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}