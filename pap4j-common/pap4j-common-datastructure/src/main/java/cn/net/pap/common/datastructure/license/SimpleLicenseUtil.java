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

public class SimpleLicenseUtil {

    // 使用URL安全的Base64编码器
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    // 新分隔符定义
    private static final String PROPERTY_SEPARATOR = "|";
    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final String CONTENT_SIGNATURE_SEPARATOR = "~";


    private static final String LICENSE_SALT = "pap.net.cn";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static boolean checkLicense(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--license=")) {
                String licenseWithSignature = arg.substring("--license=".length());
                return validateLicenseKey(licenseWithSignature);
            }
        }
        return false;
    }

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
     * 解析License内容为键值对
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
     * 生成带签名的License
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

    // HMAC计算方法保持不变
    private static String calculateHMAC(String data, String salt) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);

        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return BASE64_ENCODER.encodeToString(rawHmac);
    }

    // 日期验证方法保持不变
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
