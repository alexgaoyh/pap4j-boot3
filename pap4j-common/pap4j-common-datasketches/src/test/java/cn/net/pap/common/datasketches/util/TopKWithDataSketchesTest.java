package cn.net.pap.common.datasketches.util;

import org.apache.datasketches.frequencies.ErrorType;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static org.springframework.test.util.AssertionErrors.assertTrue;


public class TopKWithDataSketchesTest {

    @Test
    public void readTxtTestDetailed() throws IOException {
        ItemsSketch<String> sketch = new ItemsSketch<>(256);
        String filePath = "C:\\Users\\86181\\Desktop\\gcd.txt";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                for (char c : line.toCharArray()) {
                    if (Character.isWhitespace(c)) {
                        continue;
                    }
                    String charStr = String.valueOf(c);
                    sketch.update(charStr);
                }
            }
        }

        // 获取Top 100
        ItemsSketch.Row<String>[] topItems = sketch.getFrequentItems(2, ErrorType.NO_FALSE_POSITIVES);
        System.out.println("\nTop " + topItems.length + " items:");
        for (ItemsSketch.Row<String> item : topItems) {
            System.out.println("Item: '" + escapeSpecialChars(item.getItem()) +
                    "', Est. Frequency: " + item.getEstimate());
        }
    }

    private String escapeSpecialChars(String str) {
        return str.replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
    }

    @Test
    public void testTopKStrings() {
        // 创建一个容量为128的ItemsSketch实例用于统计字符串频率
        // 这个容量参数k会影响精度和内存使用
        ItemsSketch<String> sketch = new ItemsSketch<>(128);

        // 模拟数据流：更新sketch，模拟输入一些数据
        List<String> streamData = Arrays.asList(
                "apple", "banana", "orange", "apple", "grape",
                "banana", "apple", "kiwi", "orange", "apple",
                "mango", "banana", "apple", "orange", "pear",
                "grape", "apple", "banana", "orange", "apple"
        );

        // 将数据流中的每个元素添加到sketch中
        for (String item : streamData) {
            sketch.update(item);
        }

        // 获取最频繁的3个项（Top-3）
        // FrequentItemsResult数组按估计频率降序排列
        ItemsSketch.Row<String>[] topItems = sketch.getFrequentItems(3, ErrorType.NO_FALSE_POSITIVES);

        // 打印Top-K结果用于验证
        System.out.println("Top " + topItems.length + " items:");
        for (ItemsSketch.Row<String> item : topItems) {
            System.out.println("Item: " + item.getItem() + ", Est. Frequency: " + item.getEstimate());
        }

        // 进行断言验证：检查"apple"是否是出现最频繁的项
        assertTrue("Top item should be 'apple'", topItems.length > 0 && "apple".equals(topItems[0].getItem()));
        // 验证我们得到了请求数量的Top项（最多3个）
        assertTrue("Should have at most 3 top items", topItems.length <= 3);
        // 可以进一步验证频率值是否合理
        // 由于是模拟数据，我们知道"apple"出现了7次
        assertTrue("Apple should have estimated frequency of at least 7", topItems[0].getEstimate() >= 7);
    }

    @Test
    public void testTopKWithIntegerData() {
        // ItemsSketch也可以用于其他类型，比如Integer
        ItemsSketch<Integer> intSketch = new ItemsSketch<>(64); // 较小的k

        // 更新一些整数数据
        for (int i = 0; i < 100; i++) {
            intSketch.update(i % 10); // 数字0-9会循环出现
        }

        // 获取最频繁的2个数字（我们知道0-9每个都出现了10次，但sketch会返回所有，我们取Top2）
        ItemsSketch.Row<Integer>[] topNumbers = intSketch.getFrequentItems(2, ErrorType.NO_FALSE_POSITIVES);

        System.out.println("Top " + topNumbers.length + " numbers:");
        for (ItemsSketch.Row<Integer> num : topNumbers) {
            System.out.println("Number: " + num.getItem() + ", Est. Frequency: " + num.getEstimate());
        }

        // 由于哈希冲突和近似算法，频率是估计值，但应该接近10
        for (ItemsSketch.Row<Integer> num : topNumbers) {
            assertTrue("Frequency should be around 10", Math.abs(num.getEstimate() - 10) <= 2); // 允许一些误差
        }
    }
}