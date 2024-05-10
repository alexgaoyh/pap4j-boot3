package cn.net.pap.common.opencv;

/**
 * 相似度算法
 */
public class SimilarityUtils {

    /**
     * 余弦相似度  返回1代表同向的，没有任何差异；     返回0代表是正交的，没有相关性；
     *
     * @param a
     * @param b
     * @return
     */
    public static double cosineSimilarity(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return 0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dotProduct / (normA * normB);
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dotProduct / (normA * normB);
    }
}
