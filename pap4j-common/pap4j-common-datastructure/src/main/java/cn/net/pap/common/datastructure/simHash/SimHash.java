package cn.net.pap.common.datastructure.simHash;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class SimHash {

    private static final int HASH_BITS = 64; // 位数，通常64位

    /**
     * 计算SimHash值
     *
     * @param input 输入文本
     * @return SimHash签名
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
     * 计算汉明距离（Hamming Distance），用于比较两个SimHash签名的相似度
     *
     * @param hash1 SimHash签名1
     * @param hash2 SimHash签名2
     * @return 汉明距离
     */
    public static int hammingDistance(BigInteger hash1, BigInteger hash2) {
        BigInteger x = hash1.xor(hash2); // 取两个SimHash的异或
        return x.bitCount(); // 计算1的个数，即不同的位数
    }

    /**
     * 使用SHA-256对字符串进行哈希处理，并截取前8个字节
     *
     * @param input 输入字符串
     * @return 64位的哈希值
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

