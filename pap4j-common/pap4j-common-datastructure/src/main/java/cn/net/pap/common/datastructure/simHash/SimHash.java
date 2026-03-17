package cn.net.pap.common.datastructure.simHash;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * <p><strong>SimHash</strong> 是局部敏感哈希 (LSH) 算法的实现。</p>
 *
 * <p>它用于检测近似重复的文档或字符串。与加密哈希不同（在加密哈希中，微小的更改会导致截然不同的哈希值），
 * SimHash 保证相似的输入产生相似的哈希值。</p>
 *
 * <ul>
 *     <li>为简单起见，按字符对输入进行分词。</li>
 *     <li>计算 64 位整数签名。</li>
 *     <li>使用汉明距离评估相似度。</li>
 * </ul>
 */
public class SimHash {

    private static final int HASH_BITS = 64; // 位数，通常64位

    /**
     * <p>计算给定输入字符串的 SimHash 签名。</p>
     *
     * <p>此方法将文本分解为单个字符，计算每个字符的频率，并将它们的加权 SHA-256 哈希值累加到最终的 64 位签名中。</p>
     *
     * @param input 文本输入。
     * @return 表示 64 位 SimHash 签名的 {@link BigInteger}。
     */
    public static BigInteger computeSimHash(String input) {
        // 分词（简单基于字符分割，实际应用可以替换为更复杂的分词器）
        String[] words = input.split("");  // 中文中每个字符可以看作一个“词”

        // 词频统计
        Map<String, Integer> wordFrequency = new HashMap<>();
        for (String word : words) {
            wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
        }

        // 初始化向量数组
        int[] vector = new int[HASH_BITS];

        // 对每个词的哈希值进行加权处理
        for (Map.Entry<String, Integer> entry : wordFrequency.entrySet()) {
            String word = entry.getKey();
            int frequency = entry.getValue();
            BigInteger hash = hash(word); // 词的哈希值
            // 对哈希值每一位进行加权叠加
            for (int i = 0; i < HASH_BITS; i++) {
                if (hash.testBit(i)) {
                    vector[i] += frequency;  // 词频为正数，权重大于零
                } else {
                    vector[i] -= frequency;  // 词频为负数，权重小于零
                }
            }
        }

        // 生成SimHash签名
        BigInteger simHash = new BigInteger("0");
        for (int i = 0; i < HASH_BITS; i++) {
            if (vector[i] > 0) {
                simHash = simHash.setBit(i);
            }
        }
        return simHash;
    }

    /**
     * <p>计算两个 SimHash 签名之间的汉明距离。</p>
     *
     * <p>汉明距离表示两个签名中不同的位位置数。
     * 距离越小表明相似度越高。</p>
     *
     * @param hash1 第一个 SimHash 签名。
     * @param hash2 第二个 SimHash 签名。
     * @return 整数形式的汉明距离。
     */
    public static int hammingDistance(BigInteger hash1, BigInteger hash2) {
        BigInteger x = hash1.xor(hash2); // 取两个SimHash的异或
        return x.bitCount(); // 计算1的个数，即不同的位数
    }

    /**
     * <p>使用 SHA-256 对字符串进行哈希处理，并提取前 8 个字节用于 64 位哈希。</p>
     *
     * @param input 要哈希处理的字符串。
     * @return 正数 64 位 {@link BigInteger} 表示。
     */
    private static BigInteger hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes("UTF-8"));

            // 截取前8个字节作为64位哈希
            Integer length = 8;
            byte[] first8Bytes = new byte[length];
            System.arraycopy(digest, 0, first8Bytes, 0, length);

            return new BigInteger(1, first8Bytes); // 返回正数的BigInteger
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("无法计算哈希值", e);
        }
    }

}
