package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;
public class VertorUtilTest {

    // @Test
    public void testQuery2() throws Exception {

        Map<String, float[]> vectorMap = new LinkedHashMap<>();

        float[] firstVector = null;

        File file = new File("C:\\Users\\86181\\Desktop\\dir");
        File[] files = file.listFiles();

        for(File imageAbsPath : files) {
            float[] vectors = VectorUtil.convertImageToVector(imageAbsPath.getPath());
            String name = imageAbsPath.getName();
            vectorMap.put(name, vectors);
            if(name.equals("王.jpg")) {
                firstVector = vectors;
            }
        }

        Map<String, Double> similarityMap = new LinkedHashMap<>();
        for(Map.Entry<String, float[]> entry : vectorMap.entrySet()) {
            double v = SimilarityUtils.cosineSimilarity(firstVector, entry.getValue());
            similarityMap.put(entry.getKey(), v);
        }

        // 将Map转换为List
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(similarityMap.entrySet());

        // 根据value进行递减排序
        Collections.sort(sortedEntries, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> e1, Map.Entry<String, Double> e2) {
                return e2.getValue().compareTo(e1.getValue());
            }
        });

        // 打印排序后的结果
        for (Map.Entry<String, Double> entry : sortedEntries) {
            System.out.println(entry.getKey());
        }
    }
}
