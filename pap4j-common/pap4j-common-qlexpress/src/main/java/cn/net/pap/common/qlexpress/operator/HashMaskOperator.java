package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * <p>QLExpress 扩展函数：不可逆脱敏/指纹，对值做哈希（默认小写十六进制输出）。</p>
 * <p>算法使用 {@link MessageDigest} 支持的标准算法名，如 MD5 / SHA-256 / SHA-512；建议日常脱敏用 SHA-256。</p>
 * <p>salt 盐值拼接在值前，用于避免常见值（手机号等）的彩虹表攻击；固定盐值保证同值同哈希（可做去重/关联）。</p>
 * <p>值为 null 时返回 null；算法不支持时抛错。</p>
 * <p>用法：HASH_MASK(json.phone, 'SHA-256', 'mySalt')</p>
 */
public class HashMaskOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 2 || parameters.size() > 3) {
            throw new IllegalArgumentException("HASH_MASK 需要 2~3 个参数：值, 算法[, 盐值]");
        }

        Object value = parameters.get(0).get();
        Object algorithm = parameters.get(1).get();
        if (value == null) {
            return null;
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("HASH_MASK 算法不能为 null");
        }
        String salt = parameters.size() == 3 && parameters.get(2).get() != null
                ? parameters.get(2).get().toString() : "";

        String input = salt + value;
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toString());
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("HASH_MASK 不支持的算法: " + algorithm, e);
        }
    }
}
