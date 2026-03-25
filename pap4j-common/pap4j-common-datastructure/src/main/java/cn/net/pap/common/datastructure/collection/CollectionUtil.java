package cn.net.pap.common.datastructure.collection;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <h1>集合工具类 (Collection Utility)</h1>
 * <p>提供了一系列操作集合、层级节点、分批处理及排序的实用静态方法。</p>
 * <ul>
 *     <li>列表分组: {@link #groupList(List, int)}</li>
 *     <li>层级节点计算: {@link #getNextChild(String)}, {@link #getNextSibling(String)}, {@link #exitThenGetNextSibling(String)}</li>
 *     <li>分批处理: {@link #batchNoResult(List, int, Consumer)}, {@link #batchWithResult(List, int, Function)}, {@link #batchByProperty(List, int, Function)}</li>
 *     <li>有序集合构建及自定义排序: {@link #ofOrdered(Object...)}, {@link #sortByOrderList(List, List, String)}</li>
 * </ul>
 *
 * @author
 * @since
 */
public class CollectionUtil {

    /**
     * <p>将一个大的字符串列表按照指定的大小分割成多个较小的子列表。</p>
     * <strong>示例:</strong>
     * <pre>{@code
     * List<String> list = Arrays.asList("a", "b", "c", "d", "e");
     * List<List<String>> result = CollectionUtil.groupList(list, 2);
     * // 结果: [["a", "b"], ["c", "d"], ["e"]]
     * }</pre>
     *
     * @param largeList 要分组的大列表，如果为 {@code null} 或空，则返回空集合
     * @param groupSize 每个分组的最大元素数量，必须大于 0
     * @return 包含分组后子列表的列表容器，永远不会返回 {@code null}
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
     * <p>获取当前层级节点的下一个子节点（即追加子层级编号 {@code .1}）。</p>
     * <strong>示例:</strong>
     * <ul>
     *     <li>输入 {@code "1"} -> 输出 {@code "1.1"}</li>
     *     <li>输入 {@code "1.2"} -> 输出 {@code "1.2.1"}</li>
     * </ul>
     *
     * @param currentLevel 当前层级的编号字符串，例如 {@code "1.2"}
     * @return 当前层级的第一个子节点编号字符串
     */
    public static String getNextChild(String currentLevel) {
        return currentLevel + "." + 1;
    }

    /**
     * <p>获取当前层级节点的下一个兄弟节点（即最后一位数字加 {@code 1}）。</p>
     * <strong>示例:</strong>
     * <ul>
     *     <li>输入 {@code "1.1"} -> 输出 {@code "1.2"}</li>
     *     <li>输入 {@code "1.2.3"} -> 输出 {@code "1.2.4"}</li>
     * </ul>
     *
     * @param currentLevel 当前层级的编号字符串，以点号 {@code .} 分隔
     * @return 当前层级的下一个兄弟节点编号字符串
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
     * <p>跳出当前层级，并在上一个层级的数值基础上加 {@code 1}，即获取父节点的下一个兄弟节点。</p>
     * <strong>注意：</strong>如果当前是最顶层（无父节点，例如 {@code "1"}），则抛出异常。
     * <br>
     * <strong>示例:</strong>
     * <ul>
     *     <li>输入 {@code "1.2.3"} -> 输出 {@code "1.3"}</li>
     *     <li>输入 {@code "2.4"} -> 输出 {@code "3"}</li>
     * </ul>
     *
     * @param currentLevel 当前层级的编号字符串，以点号 {@code .} 分隔
     * @return 跳出当前层级后的上层兄弟节点编号字符串
     * @throws IllegalArgumentException 如果当前层级是最顶层，无法向上跳出
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
     * <p>对列表数据进行分批处理（无返回值）。</p>
     * <p>非常适用于以下场景：</p>
     * <ul>
     *     <li>大批量数据分批入库 (如 JDBC / MyBatis 批量插入)</li>
     *     <li>大批量数据分批更新</li>
     *     <li>分批调用外部 API 接口，避免因单次请求数据量过大导致超时或内存溢出</li>
     * </ul>
     *
     * @param dataList      全量数据集合，如果为空则直接返回
     * @param batchSize     每批次处理的元素最大数量，必须大于 0
     * @param batchConsumer 针对每一批次数据的回调消费逻辑
     * @param <T>           集合中元素的类型
     * @throws IllegalArgumentException 如果 {@code batchSize <= 0}
     */
    public static <T> void batchNoResult(List<T> dataList, int batchSize, Consumer<List<T>> batchConsumer) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than 0");
        }
        int size = dataList.size();
        for (int i = 0; i < size; i += batchSize) {
            List<T> batch = dataList.subList(i, Math.min(i + batchSize, size));
            batchConsumer.accept(batch);
        }
    }

    /**
     * <p>对查询条件进行分批处理，并将分批执行的结果进行汇总返回。</p>
     * <p>适用于以下场景：</p>
     * <ul>
     *     <li>解决数据库中巨型 {@code IN} 查询语句报错（如 Oracle 的 IN 限制 1000 个）</li>
     *     <li>分批通过 ID 列表拉取外部系统数据并聚合成单一结果集</li>
     * </ul>
     *
     * @param paramList     全量查询条件的集合（例如需要查询的 ID 列表）
     * @param batchSize     每批次查询的参数数量上限，必须大于 0
     * @param batchFunction 每一批次执行查询的业务逻辑函数，入参为分批参数集，返回该批次的结果集
     * @param <T>           查询参数的泛型类型
     * @param <R>           返回结果中单条记录的泛型类型
     * @return 所有批次结果汇总后的全量结果列表；如果 {@code paramList} 为空，则返回空列表
     * @throws IllegalArgumentException 如果 {@code batchSize <= 0}
     */
    public static <T, R> List<R> batchWithResult(List<T> paramList, int batchSize, Function<List<T>, Collection<R>> batchFunction) {
        if (paramList == null || paramList.isEmpty()) {
            return Collections.emptyList();
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than 0");
        }
        List<R> resultList = new ArrayList<>();
        int size = paramList.size();
        for (int i = 0; i < size; i += batchSize) {
            List<T> batch = paramList.subList(i, Math.min(i + batchSize, size));
            Collection<R> batchResult = batchFunction.apply(batch);
            if (batchResult != null && !batchResult.isEmpty()) {
                resultList.addAll(batchResult);
            }
        }
        return resultList;
    }

    /**
     * <p>安全分批处理方法，支持流式转换和非空过滤。</p>
     * <p>将原始集合按指定大小进行分批，并在分批的同时提取元素的特定属性转换为新类型结果。<br>
     * 在属性提取期间发生的异常将被转换为 {@code RuntimeException} 抛出。</p>
     *
     * @param list              原始待处理的集合（允许为 {@code null}，返回空集合）
     * @param batchSize         每批处理的大小，必须为正数
     * @param propertyExtractor 将泛型 {@code T} 转换为泛型 {@code R} 的提取函数，不可为 {@code null}
     * @param <T>               原始集合元素的类型
     * @param <R>               转换后的目标元素类型
     * @return 包含提取后数据的分批结果列表，最外层集合是不可变的，并且保证内部不包含 {@code null} 的批次结果
     * @throws IllegalArgumentException 如果 {@code batchSize} 非正数
     * @throws NullPointerException     如果 {@code propertyExtractor} 为 {@code null}
     * @throws RuntimeException         如果在提取属性时发生受检异常
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
     * <p>按照类似 {@code Map.of("key1", value1, "key2", value2)} 的思路构建一个有序字典 {@link LinkedHashMap}。</p>
     * <p>由于 Java 9 之前的 {@code Map.of} 不支持、或者对保持插入顺序有诉求时，可以使用该方法快速构建并保证迭代顺序与传入参数顺序一致。</p>
     * <strong>示例:</strong>
     * <pre>{@code
     * Map<String, Object> map = CollectionUtil.ofOrdered("id", 1, "name", "John");
     * }</pre>
     *
     * @param kv 必须成稳出现的键值对可变参数。偶数索引处为键（将转换为 {@code String}），奇数索引处为值。
     * @return 构建好的按插入顺序排序的 {@link LinkedHashMap} 对象
     * @throws IllegalArgumentException 如果传入的参数总数不是偶数（不成对）
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
     * <p>根据自定义的指定顺序列表 {@code orderList}，对包含字典数据的 {@code mapList} 进行排序。</p>
     * <p>该方法为<strong>稳定排序</strong>。如果字典中指定 {@code key} 的值在 {@code orderList} 中不存在，则排到集合的末尾。</p>
     * <strong>示例场景:</strong>
     * <p>需要将数据列表按照特定的 ID 序列 {@code [3, 1, 2]} 重新排序显示：</p>
     * <pre>{@code
     * List<Map<String, Object>> dataList = ...; // 包含 id 分别为 1, 2, 3, 4 的数据
     * CollectionUtil.sortByOrderList(dataList, Arrays.asList(3, 1, 2), "id");
     * // 排序后的顺序对应 ID 将是: 3, 1, 2, 4
     * }</pre>
     *
     * @param mapList   需要被排序的包含 {@link Map} 字典的列表数据（排序直接在该引用上原地生效）
     * @param orderList 作为排序基准的参考顺序列表
     * @param key       字典 {@link Map} 中用来提取值以与 {@code orderList} 进行匹配的键名
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

    /**
     * 通用的有序 Map 构造工具
     * @param <K> 期望的 Key 类型
     * @param <V> 期望的 Value 类型
     * @param kv  交替的 Key-Value 数组
     * @return 泛型化的 LinkedHashMap
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> ofOrderedGeneric(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("key/value must be pairs");
        }
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            // 直接强制转换，调用方需保证传入类型与声明类型一致
            map.put((K) kv[i], (V) kv[i + 1]);
        }
        return map;
    }

    /**
     * <h2>逻辑段落页码回填工具</h2>
     * * <p>该方法采用<b>逆序遍历算法</b>（Backward Fill），将 List 中键为 {@code null} 的逻辑段落映射到其下方出现的第一个有效物理页码（Integer Key）上。</p>
     * * <h3>核心逻辑说明：</h3>
     * <ul>
     * <li><b>扫描方向：</b> 从列表末尾向开头扫描 ($O(n)$ 时间复杂度)。</li>
     * <li><b>状态维护：</b> 记录扫描过程中遇到的最近一个非空 Integer 键作为“锚点”。</li>
     * <li><b>替换规则：</b> 当遇到包含 {@code null} 键的 Map 时，若下方已发现有效锚点，则将该 {@code null} 键替换为锚点键。</li>
     * </ul>
     * * <h3>处理场景示例：</h3>
     * <table border="1">
     * <tr><th>处理前 (JSON 简述)</th><th>处理后 (页码回填)</th><th>说明</th></tr>
     * <tr><td>{@code [{null: "A"}, {10: "B"}]}</td><td>{@code [{10: "A"}, {10: "B"}]}</td><td>向上回填</td></tr>
     * <tr><td>{@code [{null: "A"}, {null: "B"}, {5: "C"}]}</td><td>{@code [{5: "A"}, {5: "B"}, {5: "C"}]}</td><td>连续 null 统一锚定</td></tr>
     * <tr><td>{@code [{1: "A"}, {null: "B"}]}</td><td>{@code [{1: "A"}, {null: "B"}]}</td><td>末尾无锚点，保持原样</td></tr>
     * </table>
     * * <h3>注意事项：</h3>
     * <blockquote>
     * 1. <b>原地修改：</b> 该方法会直接修改传入的 {@code List} 及其内部的 {@code Map} 实例。<br>
     * 2. <b>多键兼容：</b> 若某 Map 包含多个物理页码（跨页段落），该方法默认取其遇到的第一个有效 Key 作为后续 null 段落的锚点。<br>
     * 3. <b>Map 类型限制：</b> 传入的 Map 实现必须支持 {@code remove()} 和 {@code put()} 操作（如 {@link java.util.HashMap}），且允许 {@code null} 键。
     * </blockquote>
     *
     * @param list 待处理的逻辑段落列表。列表项为 Map，键代表物理页码（Integer），值代表文本内容。
     * 其中键为 {@code null} 表示该段落在物理图像上无直接对应页码（如校勘记、补文等）。
     * @throws UnsupportedOperationException 如果列表中的 Map 是不可变的（如 {@code Map.of()} 创建的实例）。
     */
    public static void fillNullKeys(List<Map<Integer, String>> list) {
        // 记录“后方” Map 中出现的第一个有效页码
        Integer nextValidKey = null;

        // 从后往前遍历列表
        for (int i = list.size() - 1; i >= 0; i--) {
            Map<Integer, String> currentMap = list.get(i);

            // 1. 处理 null 键：如果存在 null，则用后方记录的 nextValidKey 替换
            if (currentMap.containsKey(null)) {
                String content = currentMap.remove(null);
                if (nextValidKey != null) {
                    currentMap.put(nextValidKey, content);
                } else {
                    // 如果后面没有有效页码，可以根据业务逻辑保留 null 或赋予默认值
                    currentMap.put(null, content);
                }
            }

            // 2. 更新 nextValidKey：取当前 Map 中第一个非空的 Key，使用 findFirst() 代替 min() 以保持插入顺序/自然出现顺序
            nextValidKey = currentMap.keySet().stream()
                    .filter(Objects::nonNull)
                    .findFirst()         // 取得第一个出现的有效键
                    .orElse(nextValidKey); // 如果当前 Map 全空，则沿用后面的有效键
        }
    }

    /**
     * <p>私有辅助方法，尝试将任意对象安全地转换为 {@link Integer} 类型。</p>
     * <p>支持将以下类型进行转换：</p>
     * <ul>
     *     <li>{@link Integer} (直接返回)</li>
     *     <li>{@link Number} (调用 {@code intValue()})</li>
     *     <li>{@link String} (调用 {@code Integer.parseInt()})</li>
     * </ul>
     *
     * @param obj 待转换的对象
     * @return 转换后的 {@link Integer} 值，如果类型不支持则返回 {@code null}
     * @throws NumberFormatException 如果是无法解析为数字的字符串
     */
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
