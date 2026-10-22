package cn.net.pap.common.core.util;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 集合处理工具类
 * <p>
 * 基于纯 JDK 集合框架实现，提供无依赖的集合算法。
 * </p>
 */
public final class CollectionUtil {

    private CollectionUtil() {
        // 私有构造器，防止实例化
    }

    /**
     * 为有序 Map 列表中的空键（null key）自动填充为后续第一个有效键值。
     * <p>
     * 倒序遍历列表，将包含 null key 的条目替换为后方最近的有效键，常用于报表或分层树状数据解析。
     * </p>
     *
     * @param list 待处理的 Map 列表
     */
    public static void fillNullKeys(List<Map<Integer, String>> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Integer nextValidKey = null;

        for (int i = list.size() - 1; i >= 0; i--) {
            Map<Integer, String> currentMap = list.get(i);
            if (currentMap == null) {
                continue;
            }

            // 1. 若当前 Map 包含 null 键，用后方记录的 nextValidKey 替换
            if (currentMap.containsKey(null)) {
                String content = currentMap.remove(null);
                if (nextValidKey != null) {
                    currentMap.put(nextValidKey, content);
                } else {
                    currentMap.put(null, content);
                }
            }

            // 2. 刷新 nextValidKey：获取当前 Map 中第一个非空的 Key
            nextValidKey = currentMap.keySet().stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(nextValidKey);
        }
    }
}
