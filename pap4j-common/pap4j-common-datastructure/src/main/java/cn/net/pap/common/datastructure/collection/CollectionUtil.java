package cn.net.pap.common.datastructure.collection;

import java.util.ArrayList;
import java.util.List;

/**
 * 集合工具类
 */
public class CollectionUtil {

    /**
     * 将列表按照指定大小分组
     *
     * @param largeList 要分组的大列表
     * @param groupSize 每个分组的大小
     * @return 包含分组后子列表的列表
     */
    public static List<List<String>> groupList(List<String> largeList, int groupSize) {
        List<List<String>> groupedLists = new ArrayList<>();
        if (largeList == null || largeList.isEmpty() || groupSize <= 0) {
            return groupedLists;
        }

        int fullGroupCount = largeList.size() / groupSize;
        int remainingElements = largeList.size() % groupSize;

        for (int i = 0; i < fullGroupCount; i++) {
            int start = i * groupSize;
            int end = start + groupSize;
            groupedLists.add(largeList.subList(start, end));
        }

        if (remainingElements > 0) {
            int start = fullGroupCount * groupSize;
            groupedLists.add(largeList.subList(start, largeList.size()));
        }

        return groupedLists;
    }

    /**
     * 获取下一个子节点
     *
     * @param currentLevel
     * @return
     */
    public static String getNextChild(String currentLevel) {
        return currentLevel + "." + 1;
    }

    /**
     * 获取下一个兄弟节点
     *
     * @param currentLevel
     * @return
     */
    public static String getNextSibling(String currentLevel) {
        String[] levels = currentLevel.split("\\.");
        // 获取最后一位数值并加1
        int lastLevel = Integer.parseInt(levels[levels.length - 1]) + 1;
        // 替换最后一个数值并返回新的兄弟节点
        levels[levels.length - 1] = String.valueOf(lastLevel);
        return String.join(".", levels);
    }

    /**
     * 跳出当前层级并在上一个层级中+1
     *
     * @param currentLevel
     * @return
     */
    public static String exitThenGetNextSibling(String currentLevel) {
        String[] levels = currentLevel.split("\\.");

        // 如果当前层级已经没有父层级，返回空或错误提示
        if (levels.length == 1) {
            throw new IllegalArgumentException("Cannot jump up, this is the top level.");
        }

        // 跳出到上一个层级
        levels = java.util.Arrays.copyOf(levels, levels.length - 1);

        // 在跳出的最后一层加1
        int lastLevel = Integer.parseInt(levels[levels.length - 1]) + 1;
        levels[levels.length - 1] = String.valueOf(lastLevel);

        // 返回新的层级
        return String.join(".", levels);
    }


}
