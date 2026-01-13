package cn.net.pap.common.excel.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * list map 分组
 */
public class ListMapGroupUtil {

    public static class GroupOptions {

        private String childrenFieldName = "_children";

        private boolean keepGroupFieldsInChildren = false;

        private boolean preserveOriginalOrder = true;

        public GroupOptions setChildrenFieldName(String name) {
            this.childrenFieldName = name;
            return this;
        }

        public GroupOptions setKeepGroupFieldsInChildren(boolean keep) {
            this.keepGroupFieldsInChildren = keep;
            return this;
        }

        public GroupOptions setPreserveOriginalOrder(boolean preserve) {
            this.preserveOriginalOrder = preserve;
            return this;
        }
    }

    public static List<Map<String, Object>> groupByFields(List<Map<String, Object>> dataList, String[] groupFields, GroupOptions options) {

        if (options == null) {
            options = new GroupOptions();
        }

        String childrenFieldName = options.childrenFieldName;
        boolean keepGroupFields = options.keepGroupFieldsInChildren;
        boolean preserveOrder = options.preserveOriginalOrder;

        // 使用LinkedHashMap保持插入顺序
        Map<String, Map<String, Object>> groupMap = preserveOrder ? new LinkedHashMap<>() : new HashMap<>();

        Map<String, Integer> orderMap = preserveOrder ? new LinkedHashMap<>() : new HashMap<>();

        for (Map<String, Object> item : dataList) {
            String groupKey = buildGroupKey(item, groupFields);

            if (!groupMap.containsKey(groupKey)) {
                Map<String, Object> groupNode = preserveOrder ? new LinkedHashMap<>() : new HashMap<>();

                // 添加分组字段
                for (String field : groupFields) {
                    groupNode.put(field, item.get(field));
                }

                groupNode.put(childrenFieldName, new ArrayList<Map<String, Object>>());
                groupMap.put(groupKey, groupNode);

                if (preserveOrder) {
                    orderMap.put(groupKey, orderMap.size());
                }
            }

            // 创建子节点
            Map<String, Object> childNode;
            if (keepGroupFields) {
                childNode = new LinkedHashMap<>(item);
            } else {
                childNode = new LinkedHashMap<>(item);
                for (String field : groupFields) {
                    childNode.remove(field);
                }
            }

            @SuppressWarnings("unchecked") List<Map<String, Object>> children = (List<Map<String, Object>>) groupMap.get(groupKey).get(childrenFieldName);
            children.add(childNode);
        }

        if (preserveOrder && orderMap != null) {
            return orderMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(entry -> groupMap.get(entry.getKey())).collect(Collectors.toList());
        } else {
            return new ArrayList<>(groupMap.values());
        }
    }

    private static String buildGroupKey(Map<String, Object> item, String[] groupFields) {
        StringBuilder keyBuilder = new StringBuilder();
        for (String field : groupFields) {
            Object value = item.get(field);
            keyBuilder.append(value != null ? value.toString() : "null").append("|");
        }
        return keyBuilder.toString();
    }

    /**
     * 简化调用方法
     *
     *         List<Map<String, Object>> result = ListMapGroupUtil.groupByFields(
     *                 dataList,
     *                 new String[]{"field1", "field2"},
     *                 new ListMapGroupUtil.GroupOptions()
     *                         .setChildrenFieldName("details")
     *                         .setKeepGroupFieldsInChildren(false)
     *         );
     *
     * @param dataList
     * @param groupFields
     * @return
     */
    public static List<Map<String, Object>> groupByFields(List<Map<String, Object>> dataList, String... groupFields) {
        return groupByFields(dataList, groupFields, new GroupOptions());
    }

}
