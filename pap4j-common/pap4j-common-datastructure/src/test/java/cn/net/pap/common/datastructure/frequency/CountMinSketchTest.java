package cn.net.pap.common.datastructure.frequency;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

public class CountMinSketchTest {

    // @Test
    public void readTxtTest() throws IOException {
        // 创建 Count-Min Sketch
        CountMinSketch cms = new CountMinSketch(0.0001, 0.9999, 1234567890);
        String filePath = "input.txt";  // 替换成你的 txt 路径
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                for (char c : line.toCharArray()) {
                    // 过滤掉换行/空格，可以按需保留
                    if (Character.isWhitespace(c)) continue;
                    // 每个字符作为 key
                    cms.add(String.valueOf(c), 1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // ====== 分析部分 ======
        // 假设要看 A-Z 以及常见字符的频率
        List<String> charsToCheck = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; c++) charsToCheck.add(String.valueOf(c));
        for (char c = 'A'; c <= 'Z'; c++) charsToCheck.add(String.valueOf(c));
        charsToCheck.addAll(Arrays.asList("你", "我", "他", "的", "，", "。")); // 也可以加入中文常见字符

        System.out.println("=== 字符频率估计 (Count-Min Sketch) ===");
        for (String c : charsToCheck) {
            long est = cms.estimateCount(c);
            if (est > 0) {
                System.out.println("'" + c + "' ≈ " + est);
            }
        }
        System.out.println("总字符数 ≈ " + cms.size());
        System.out.println("误差上限 = " + cms.getRelativeError());
        System.out.println("置信度 = " + cms.getConfidence());
    }

    @Test
    public void negativeSize() {
        new CountMinSketch(20, 4, -1, new long[]{1}, new long[][]{{10, 20}, {100, 200}});
    }

    @Test
    public void sizeOverflow() {
        CountMinSketch sketch = new CountMinSketch(0.0001, 0.99999, 1);
        sketch.add(3, 1);
        sketch.add(4, 2);
    }

    @Test
    public void testSize() throws Exception {
        CountMinSketch sketch = new CountMinSketch(0.00001, 0.99999, 1);
        assertEquals(0, sketch.size(), 0);

        sketch.add(1, 11);
        sketch.add(2, 22);
        sketch.add(3, 33);

        long expectedSize = 11 + 22 + 33;
        assertEquals(expectedSize, sketch.size());
    }

    @Test
    public void testSizeCanStoreLong() throws Exception {
        double confidence = 0.999;
        double epsilon = 0.0001;
        int seed = 1;

        CountMinSketch sketch = new CountMinSketch(epsilon, confidence, seed);

        long freq1 = Integer.MAX_VALUE;
        long freq2 = 156;

        sketch.add(1, freq1);
        sketch.add(2, freq2);

        CountMinSketch newSketch = CountMinSketch.merge(sketch, sketch);

        long expectedSize = 2 * (freq1 + freq2);
        assertEquals(expectedSize, newSketch.size());
    }

    @Test
    public void testAccuracy() {
        int seed = 7364181;
        Random r = new Random(seed);
        int numItems = 1000000;
        int[] xs = new int[numItems];
        int maxScale = 20;
        for (int i = 0; i < numItems; i++) {
            int scale = r.nextInt(maxScale);
            xs[i] = r.nextInt(1 << scale);
        }

        double epsOfTotalCount = 0.0001;
        double confidence = 0.99;

        CountMinSketch sketch = new CountMinSketch(epsOfTotalCount, confidence, seed);
        for (int x : xs) {
            sketch.add(x, 1);
        }

        int[] actualFreq = new int[1 << maxScale];
        for (int x : xs) {
            actualFreq[x]++;
        }

        sketch = CountMinSketch.deserialize(CountMinSketch.serialize(sketch));

        int numErrors = 0;
        for (int i = 0; i < actualFreq.length; ++i) {
            double ratio = ((double) (sketch.estimateCount(i) - actualFreq[i])) / numItems;
            if (ratio > epsOfTotalCount) {
                numErrors++;
            }
        }
        double pCorrect = 1.0 - ((double) numErrors) / actualFreq.length;
        assertTrue("Confidence not reached: required " + confidence + ", reached " + pCorrect, pCorrect > confidence);
    }

    @Test
    public void merge() throws Exception {
        int numToMerge = 5;
        int cardinality = 1000000;

        double epsOfTotalCount = 0.0001;
        double confidence = 0.99;
        int seed = 7364181;

        int maxScale = 20;
        Random r = new Random();
        TreeSet<Integer> vals = new TreeSet<Integer>();

        CountMinSketch baseline = new CountMinSketch(epsOfTotalCount, confidence, seed);
        CountMinSketch[] sketchs = new CountMinSketch[numToMerge];
        for (int i = 0; i < numToMerge; i++) {
            sketchs[i] = new CountMinSketch(epsOfTotalCount, confidence, seed);
            for (int j = 0; j < cardinality; j++) {
                int scale = r.nextInt(maxScale);
                int val = r.nextInt(1 << scale);
                vals.add(val);
                sketchs[i].add(val, 1);
                baseline.add(val, 1);
            }
        }

        CountMinSketch merged = CountMinSketch.merge(sketchs);

        assertEquals(baseline.size(), merged.size());
        assertEquals(baseline.getConfidence(), merged.getConfidence(), baseline.getConfidence() / 100);
        assertEquals(baseline.getRelativeError(), merged.getRelativeError(), baseline.getRelativeError() / 100);
        for (int val : vals) {
            assertEquals(baseline.estimateCount(val), merged.estimateCount(val));
        }
    }

    @Test
    public void testMergeEmpty() throws Exception {
        assertNull(CountMinSketch.merge());
    }

    @Test
    public void testUncompatibleMerge() throws Exception {
        CountMinSketch cms1 = new CountMinSketch(1, 1, 0);
        CountMinSketch cms2 = new CountMinSketch(1, 1, 0);
        CountMinSketch.merge(cms1, cms2);
    }

    private static void checkCountMinSketchSerialization(CountMinSketch cms) throws IOException, ClassNotFoundException {
        byte[] bytes = CountMinSketch.serialize(cms);
        CountMinSketch serializedCms = (CountMinSketch)CountMinSketch.deserialize(bytes);

        assertEquals(cms.eps, serializedCms.eps);
    }

    @Test
    public void testSerializationForDepthCms() throws IOException, ClassNotFoundException {
        checkCountMinSketchSerialization(new CountMinSketch(12, 2045, 1));
    }

    @Test
    public void testSerializationForConfidenceCms() throws IOException, ClassNotFoundException {
        checkCountMinSketchSerialization(new CountMinSketch(0.0001, 0.99999999999, 1));
    }

    @Test
    public void testEquals() {
        double eps1 = 0.0001;
        double eps2 = 0.000001;
        double confidence = 0.99;
        int seed = 1;

        final CountMinSketch sketch1 = new CountMinSketch(eps1, confidence, seed);
        assertEquals(sketch1, sketch1);

        final CountMinSketch sketch2 = new CountMinSketch(eps1, confidence, seed);
        assertEquals(sketch1, sketch2);

        assertNotEquals(sketch1, null);

        sketch1.add(1, 123);
        sketch2.add(1, 123);
        assertEquals(sketch1, sketch2);

        sketch1.add(1, 4);
        assertNotEquals(sketch1, sketch2);

        final CountMinSketch sketch4 = new CountMinSketch(eps1, confidence, seed);
        final CountMinSketch sketch5 = new CountMinSketch(eps2, confidence, seed);
        assertNotEquals(sketch4, sketch5);

        sketch4.add(1, 7);
        assertNotEquals(sketch4, sketch5);
    }

    @Test
    public void testToString() {
        double eps = 0.0001;
        double confidence = 0.99;
        int seed = 1;

        final CountMinSketch sketch = new CountMinSketch(eps, confidence, seed);
        assertEquals("CountMinSketch{" +
                "eps=" + eps +
                ", confidence=" + confidence +
                ", depth=" + 7 +
                ", width=" + 20000 +
                ", size=" + 0 +
                '}', sketch.toString());

        sketch.add(12, 145);
        assertEquals("CountMinSketch{" +
                "eps=" + eps +
                ", confidence=" + confidence +
                ", depth=" + 7 +
                ", width=" + 20000 +
                ", size=" + 145 +
                '}', sketch.toString());
    }
}
