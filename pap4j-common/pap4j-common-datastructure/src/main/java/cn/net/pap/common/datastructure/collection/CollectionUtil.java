package cn.net.pap.common.datastructure.collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    /**
     * 安全分批处理方法
     *
     * @param list              原始集合（可为空）
     * @param batchSize         每批大小（必须大于0）
     * @param propertyExtractor 属性提取函数（不可为null）
     * @param <T>               原始元素类型
     * @param <R>               结果元素类型
     * @return 分批后的结果列表（永远不会返回null）
     * @throws IllegalArgumentException 如果参数不合法
     */
    public static <T, R> List<List<R>> batchByProperty(List<T> list, int batchSize, Function<T, R> propertyExtractor) {
        Objects.requireNonNull(propertyExtractor, "Property extractor cannot be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        final List<T> unmodifiableList = Collections.unmodifiableList(list);

        return IntStream.range(0, (unmodifiableList.size() + batchSize - 1) / batchSize).mapToObj(i -> {
            int start = i * batchSize;
            int end = Math.min(unmodifiableList.size(), start + batchSize);
            return unmodifiableList.subList(start, end).stream().map(element -> {
                try {
                    return propertyExtractor.apply(element);
                } catch (Exception e) {
                    // 记录日志或处理提取异常
                    throw new RuntimeException(e);
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        }).filter(batch -> !batch.isEmpty()).collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * 按照 Map.of("id", 1, "name", "D") 的思路，生成 LinkedHashMap 对象
     * @param kv
     * @return
     */
    public static Map<String, Object> ofOrdered(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("key/value must be pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }


    /**
     * 根据指定的顺序列表 orderList，对 mapList 中的元素按 map[key] 的值进行排序。 稳定排序
     *
     * @param mapList   需要排序的 List<Map>
     * @param orderList 排序基准的顺序列表，如 [3,1,2]
     * @param key       Map 中用来与 orderList 匹配的键
     */
    public static void sortByOrderList(List<Map<String, Object>> mapList, List<Integer> orderList, String key) {
        if (mapList == null || orderList == null || key == null) {
            return;
        }

        Map<Integer, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < orderList.size(); i++) {
            orderIndex.put(orderList.get(i), i);
        }

        // 排序
        mapList.sort((m1, m2) -> {
            Integer v1 = getInt(m1.get(key));
            Integer v2 = getInt(m2.get(key));

            // 不在 orderList 中的，排到最后
            int i1 = orderIndex.getOrDefault(v1, Integer.MAX_VALUE);
            int i2 = orderIndex.getOrDefault(v2, Integer.MAX_VALUE);

            return Integer.compare(i1, i2);
        });
    }

    private static Integer getInt(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        return null;
    }

}
