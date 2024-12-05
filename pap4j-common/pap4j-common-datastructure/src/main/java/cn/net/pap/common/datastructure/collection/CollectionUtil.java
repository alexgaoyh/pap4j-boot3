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

}
