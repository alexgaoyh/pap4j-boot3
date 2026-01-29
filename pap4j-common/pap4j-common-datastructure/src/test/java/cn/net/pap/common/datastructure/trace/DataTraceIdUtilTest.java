package cn.net.pap.common.datastructure.trace;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataTraceIdUtilTest {

    @Test
    public void test1() {
        String root = DataTraceIdUtil.generateRoot("ORD1", "S001");
        System.out.println("Root traceId: " + root + " -> " + DataTraceIdUtil.parse(root));

        List<String> children = DataTraceIdUtil.deriveBranches(root, 3);
        for (String t : children) {
            System.out.println("Derived: " + t + " -> " + DataTraceIdUtil.parse(t));
        }

        // 再派生第二层
        List<String> nextLevel = DataTraceIdUtil.deriveBranches(children.get(0), 2);
        for (String t : nextLevel) {
            System.out.println("Next Level Derived: " + t + " -> " + DataTraceIdUtil.parse(t));
        }
    }

    @Test
    public void test2() {
        String root = DataTraceIdUtil.generateRoot("ORD1", "S001");
        System.out.println("Root: " + root + " -> " + DataTraceIdUtil.parse(root));

        // 新增一步
        String step1 = DataTraceIdUtil.nextStep(root);
        System.out.println("Next Step 1: " + step1 + " -> " + DataTraceIdUtil.parse(step1));

        // 再新增一步
        String step2 = DataTraceIdUtil.nextStep(step1);
        System.out.println("Next Step 2: " + step2 + " -> " + DataTraceIdUtil.parse(step2));
    }

    @Test
    public void test3() {
        // 根节点
        String root = DataTraceIdUtil.generateRoot("ORD1", "S001");
        System.out.println("Root traceId: " + root + " -> " + DataTraceIdUtil.parse(root));

        // 保存当前层级节点
        List<String> currentLevel = new ArrayList<>();
        currentLevel.add(root);

        int totalLevels = 10;     // 总共派生 10 层
        int branchesPerNode = 1;  // 每个节点派生 1 个子节点

        for (int level = 1; level <= totalLevels; level++) {
            List<String> nextLevel = new ArrayList<>();

            for (String parent : currentLevel) {
                List<String> children = DataTraceIdUtil.deriveBranches(parent, branchesPerNode);

                for (String child : children) {
                    DataTraceIdUtil.TraceMeta meta = DataTraceIdUtil.parse(child);
                    System.out.println("Parent: " + parent + " -> Child: " + child + " -> " + meta);
                    nextLevel.add(child);
                }
            }

            // 下一轮循环处理下一层
            currentLevel = nextLevel;
        }
    }

    @Test
    public void test4() {
        List<String> base62List = new ArrayList<>();
        // 3位最大值 238328
        for(int i = 1; i < 238328; i++) {
            String base62 = toBase62(i, 3);
            base62List.add(base62);
        }

        // 创建按字典序排序的新集合
        List<String> sortedList = new ArrayList<>(base62List);
        Collections.sort(sortedList);

        // 详细的比较信息
        if (true) {
            // 找出第一个不同的位置
            for (int i = 0; i < Math.min(base62List.size(), sortedList.size()); i++) {
                if (!base62List.get(i).equals(sortedList.get(i))) {
                    System.out.println("第一个不同的位置索引: " + i);
                    System.out.println("原始集合元素: " + base62List.get(i));
                    System.out.println("排序集合元素: " + sortedList.get(i));
                    break;
                }
            }
        }

    }

    private static String toBase62(long value, int length) {
        String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        do {
            int idx = (int) (value % 62);
            sb.insert(0, BASE62.charAt(idx));
            value /= 62;
        } while (value > 0);
        while (sb.length() < length) sb.insert(0, '0');
        return sb.toString();
    }

}
