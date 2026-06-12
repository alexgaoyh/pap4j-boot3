package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.resource.TestResourceUtil;
import cn.net.pap.common.datastructure.trie.DoubleArrayTrie;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DoubleArrayTrieTest {

    private static final Logger log = LoggerFactory.getLogger(DoubleArrayTrieTest.class);

    @Test
    public void simpleTest() throws Exception {

        List<String> words = new ArrayList<String>();
        words.add("一举");
        words.add("一举成名");
        words.add("一举成名天下知");
        Collections.sort(words);

        Set<Integer> charset = new HashSet<Integer>();

        log.info("字典词条：{}", words.size());

        {
            String infoCharsetValue = "";
            String infoCharsetCode = "";
            for (Integer c : charset)
            {
                infoCharsetValue += new String(Character.toChars(c)) + "    ";
                infoCharsetCode += c + " ";
            }
            infoCharsetValue += '\n';
            infoCharsetCode += '\n';
            log.info(infoCharsetValue);
            log.info(infoCharsetCode);
        }

        DoubleArrayTrie dat = new DoubleArrayTrie();
        log.info("是否错误: {}", dat.build(words));
        List<Integer> integerList = dat.commonPrefixSearch("一举成名天下知");
        for (int index : integerList) {
            log.info(words.get(index));
        }

        int idx = dat.exactMatchSearch("一举成名天下知");
        log.info(words.get(idx));
    }

    @Test
    public void test() throws Exception {
        // dict.dict 每一行是一个词
        BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(new FileInputStream(new File(TestResourceUtil.getFile("dict.dict").toPath().toAbsolutePath().toString()))));
        String line;
        List<String> words = new ArrayList<String>();
        Set<Integer> charset = new HashSet<Integer>();
        while ((line = reader.readLine()) != null)
        {
            words.add(line);
            // 制作一份码表debug
            line.codePoints().forEach(charset::add);
        }
        reader.close();
        // 这个字典如果要加入新词必须按字典序，参考下面的代码
//        Collections.sort(words);
//        BufferedWriter writer = new BufferedWriter(new FileWriter("./data/sorted.dic", false));
//        for (String w : words)
//        {
//            writer.write(w);
//            writer.newLine();
//        }
        // 显示调用，需与使用字典序.
        Collections.sort(words);

        log.info("字典词条：{}", words.size());

        {
            String infoCharsetValue = "";
            String infoCharsetCode = "";
            for (Integer c : charset)
            {
                infoCharsetValue += new String(Character.toChars(c)) + "    ";
                infoCharsetCode += c + " ";
            }
            infoCharsetValue += '\n';
            infoCharsetCode += '\n';
            log.info(infoCharsetValue);
            log.info(infoCharsetCode);
        }

        DoubleArrayTrie dat = new DoubleArrayTrie();
        log.info("是否错误: {}", dat.build(words));
        log.info("{}", dat);
        List<Integer> integerList = dat.commonPrefixSearch("一举成名天下知");
        for (int index : integerList)
        {
            log.info(words.get(index));
        }
    }

    @Test
    public void explainZeroAllocationTest() throws Exception {
        String testStr = "一举成名天下知"; // 7个字符的测试文本
        int iterations = 1000000; // 迭代 100 万次

        log.info("=========================================");
        log.info("开始测试：零堆分配 vs 高频堆分配 (100万次迭代)");
        log.info("测试数据：'{}'", testStr);

        // ---------------- 旧写法测试 (High Allocation) ----------------
        System.gc(); // 强制垃圾回收以获取干净的内存起点
        Thread.sleep(100);
        long freeMemBeforeOld = Runtime.getRuntime().freeMemory();
        long startTimeOld = System.nanoTime();

        int dummySumOld = 0;
        for (int i = 0; i < iterations; i++) {
            // ❌ 旧做法：每次都把 String 转换为一个新的 int[] 数组对象存放在堆上
            int[] cps = testStr.codePoints().toArray(); 
            dummySumOld += cps[3]; // 仅仅为了读取第3个字符
        }

        long endTimeOld = System.nanoTime();
        long freeMemAfterOld = Runtime.getRuntime().freeMemory();
        long garbageOld = Math.max(0, freeMemBeforeOld - freeMemAfterOld);

        log.info("【❌ 旧写法 (toArray)】耗时: {} ms, 产生临时堆垃圾: {} MB", 
                (endTimeOld - startTimeOld) / 1000000, garbageOld / (1024 * 1024));

        // ---------------- 新写法测试 (Zero Heap Allocation) ----------------
        System.gc(); // 再次重置内存起点
        Thread.sleep(100);
        long freeMemBeforeNew = Runtime.getRuntime().freeMemory();
        long startTimeNew = System.nanoTime();

        int dummySumNew = 0;
        for (int i = 0; i < iterations; i++) {
            // ✅ 新做法：直接在已有的 String 底层 char 数组上数数移动，完全不 new 新对象
            int cp = getCodePointAt(testStr, 3); 
            dummySumNew += cp;
        }

        long endTimeNew = System.nanoTime();
        long freeMemAfterNew = Runtime.getRuntime().freeMemory();
        long garbageNew = Math.max(0, freeMemBeforeNew - freeMemAfterNew);

        log.info("【✅ 新写法 (codePointAt)】耗时: {} ms, 产生临时堆垃圾: {} MB (理论上接近 0)", 
                (endTimeNew - startTimeNew) / 1000000, garbageNew / (1024 * 1024));
        log.info("=========================================");

        // 验证计算结果一致，防止 JVM 优化器把循环给空优化掉
        org.junit.jupiter.api.Assertions.assertEquals(dummySumOld, dummySumNew);
    }

    private static int getCodePointAt(String s, int depth) {
        int len = s.length();
        int curDepth = 0;
        for (int i = 0; i < len; ) {
            int cp = s.codePointAt(i);
            if (curDepth == depth) {
                return cp;
            }
            curDepth++;
            i += Character.charCount(cp);
        }
        return 0;
    }

}
