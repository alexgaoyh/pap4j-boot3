package cn.net.pap.common.jsonorm.util;

import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import cn.net.pap.common.jsonorm.dto.MappingTableDTO;
import cn.net.pap.common.jsonorm.dto.TableFieldValueDTO;
import cn.net.pap.common.jsonorm.dto.DelDetailTableValueDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

/**
 * JSON ORM 工具类
 */
public class JsonORMUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonORMUtil.class);

    /**
     * 文档读取 - 读取文本内容
     *
     * @param file 待读取的文件
     * @return 文件文本内容
     * @throws IOException 读取文件失败时抛出
     */
    public static String readFileToString(File file) throws IOException {
        String JSON = "";
        String nextString = "";
        try (FileInputStream stream = new FileInputStream(file);
             InputStreamReader streamReader = new InputStreamReader(stream, "UTF-8");
             BufferedReader reader = new BufferedReader(streamReader)) {
            while ((nextString = reader.readLine()) != null)
                JSON = new StringBuilder().append(JSON).append(nextString).toString();
        } catch (Exception ex) {
            log.error("读取文件异常: {}", ex.getMessage(), ex);
        }
        return JSON;
    }

    /**
     * 从 map list 里面进行遍历取值，把 多个集合中具有相同key和value不分的数据查出来，放到一个单独的 map 里面返回，
     * 入参： [{key1=value1, key2=value2}, {key1=value1, key2=value2, key3=value3}, {key1=value1}]
     * 出参： {key1=value1}
     *
     * @param mapList 待遍历的 map 列表
     * @return 所有 map 中 key-value 完全一致的公共项
     */
    public static Map<String, Object> findUniqueKeyValuePairs(List<Map<String, Object>> mapList) {
        Map<String, Object> returnMap = new LinkedHashMap<>();

        // 计算每个key对应的value出现的次数
        Map<String, List<Object>> keyValueCounts = new LinkedHashMap<>();
        for (Map<String, Object> map : mapList) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                keyValueCounts.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }

        // 构建结果列表，只包含具有唯一key-value对的项
        for (Map.Entry<String, List<Object>> entry : keyValueCounts.entrySet()) {
            if (entry.getValue() != null && entry.getValue().size() == mapList.size() && new HashSet(entry.getValue()).size() == 1) {
                returnMap.put(entry.getKey(), entry.getValue().get(0));
            }
        }

        return returnMap;
    }

    /**
     * 将 业务数据-jsonNode 按照 业务-表结构映射规则-mappingORMDTO 进行封装处理，转换为结构化操作数据
     *
     * @param mappingORMDTO 业务-表结构映射规则
     * @param jsonNode      业务数据
     * @return 结构化操作数据列表
     * @throws Exception 转换失败时抛出
     */
    public static List<TableFieldValueDTO> geneTableFieldValueDTOList(MappingORMDTO mappingORMDTO, JsonNode jsonNode) throws Exception {
        List<TableFieldValueDTO> tableFieldValueDTOList = new ArrayList<>();

        Map<String, MappingTableDTO> mappingMap = mappingORMDTO.getMapping();

        for (Map.Entry<String, MappingTableDTO> entry : mappingMap.entrySet()) {
            if(mappingORMDTO.getOperator().equals("insert") || mappingORMDTO.getOperator().equals("update") || mappingORMDTO.getOperator().equals("delete")) {
                if (entry.getValue().getFk() == null || entry.getValue().getFk().size() == 0) {
                    // 这里理解为主表，没有外键设置的表结构
                    List<Map<String, Object>> flattenedList = JsonORMUtil.flattenJson(jsonNode);
                    Map<String, Object> uniqueKeyValuePairs = JsonORMUtil.findUniqueKeyValuePairs(flattenedList);
                    TableFieldValueDTO tableFieldValueDTO = geneTableFieldValueDTO(entry.getKey(), entry.getValue(), uniqueKeyValuePairs);
                    if (tableFieldValueDTO.getSuccessInt() == 0) {
                        tableFieldValueDTO.setFk(entry.getValue().getFk());
                        tableFieldValueDTOList.add(tableFieldValueDTO);
                    }
                } else {
                    // 这里理解为附表，有外键设置的表结构
                    List<Map<String, Object>> flattenedMapList = JsonORMUtil.flattenJson(jsonNode);
                    flattenedMapList = withFKFilter(entry.getKey(), entry.getValue(), flattenedMapList);
                    for (Map<String, Object> flattenedMap : flattenedMapList) {
                        TableFieldValueDTO tableFieldValueDTO = geneTableFieldValueDTO(entry.getKey(), entry.getValue(), flattenedMap);
                        if (tableFieldValueDTO.getSuccessInt() == 0) {
                            tableFieldValueDTO.setFk(entry.getValue().getFk());
                            tableFieldValueDTOList.add(tableFieldValueDTO);
                        }
                    }
                }
            }
            if(mappingORMDTO.getOperator().equals("select")) {
                List<Map<String, Object>> flattenedList = JsonORMUtil.flattenJson(jsonNode);
                Map<String, Object> uniqueKeyValuePairs = JsonORMUtil.findUniqueKeyValuePairs(flattenedList);
                TableFieldValueDTO tableFieldValueDTO = new TableFieldValueDTO();
                tableFieldValueDTO.setTableName(entry.getKey());
                tableFieldValueDTO.setValueMap(uniqueKeyValuePairs);
                tableFieldValueDTO.setFk(entry.getValue().getField());
                tableFieldValueDTOList.add(tableFieldValueDTO);

            }

        }

        return tableFieldValueDTOList;
    }

    /**
     * 刷新生成的 TableFieldValueDTO 对象，根据主键和外键标识，将主键和外键的关联关系添加进来。
     *
     * @param tableFieldValueDTOList 生成的 TableFieldValueDTO 列表
     * @return 刷新外键关联后的列表
     * @throws Exception 刷新失败时抛出
     */
    public static List<TableFieldValueDTO> refreshTableFieldValueDTOListInsert(List<TableFieldValueDTO> tableFieldValueDTOList) throws Exception {
        List<TableFieldValueDTO> returnList = deepCopyList(tableFieldValueDTOList, TableFieldValueDTO.class);

        if(returnList != null && returnList.size() > 0) {
            returnList = returnList.stream().sorted(Comparator.comparing(l -> l.getFk() == null ? 0 : l.getFk().size(), Comparator.nullsFirst(Integer::compareTo))).collect(toList());
            Map<String, String> pkIdMap = new HashMap<>();
            for(TableFieldValueDTO tableFieldValueDTO : returnList) {
                if(!StringUtils.isEmpty(tableFieldValueDTO.getPk())) {
                    String idStr = UUID.randomUUID().toString().replace("-", "");
                    pkIdMap.put(tableFieldValueDTO.getPk(), idStr);
                    tableFieldValueDTO.getValueMap().put(tableFieldValueDTO.getPk(), idStr);
                }
                if(tableFieldValueDTO.getFk() != null && tableFieldValueDTO.getFk().size() > 0) {
                    for(String fk : tableFieldValueDTO.getFk()) {
                        if(pkIdMap.containsKey(fk)) {
                            tableFieldValueDTO.getValueMap().put(fk, pkIdMap.get(fk));
                        } else {
                            tableFieldValueDTO.setSuccessInt(2);
                            tableFieldValueDTO.setErrorMsg("刷新外键异常!");
                        }
                    }
                }
            }
        }

        return returnList;
    }


    /**
     * 刷新生成的 TableFieldValueDTO 对象，根据主键和外键标识，将主键和外键的关联关系添加进来。
     * @param tableFieldValueDTOList 生成的 TableFieldValueDTO 列表
     * @param usingInputPK  true代表使用前端传递过来的主键值(更新操作使用先删除再插入，此时可以传递true使用前端传值)；  false代表使用服务端的主键生成策略
     * @return 刷新外键关联后的列表
     * @throws Exception 刷新失败时抛出
     */
    public static List<TableFieldValueDTO> refreshTableFieldValueDTOListInsert(List<TableFieldValueDTO> tableFieldValueDTOList, Boolean usingInputPK) throws Exception {
        if(usingInputPK == false) {
            return refreshTableFieldValueDTOListInsert(tableFieldValueDTOList);
        } else {
            // 当前代码段落要求主键部分使用前端传递的值，而不是自动创建。
            List<TableFieldValueDTO> returnList = deepCopyList(tableFieldValueDTOList, TableFieldValueDTO.class);

            if(returnList != null && returnList.size() > 0) {
                returnList = returnList.stream().sorted(Comparator.comparing(l -> l.getFk() == null ? 0 : l.getFk().size(), Comparator.nullsFirst(Integer::compareTo))).collect(toList());
                Map<String, String> pkIdMap = new HashMap<>();
                for(TableFieldValueDTO tableFieldValueDTO : returnList) {
                    if(!StringUtils.isEmpty(tableFieldValueDTO.getPk())) {
                        String idStr = tableFieldValueDTO.getValueMap().get(tableFieldValueDTO.getPk()).toString();
                        pkIdMap.put(tableFieldValueDTO.getPk(), idStr);
                        tableFieldValueDTO.getValueMap().put(tableFieldValueDTO.getPk(), idStr);
                    }
                    if(tableFieldValueDTO.getFk() != null && tableFieldValueDTO.getFk().size() > 0) {
                        for(String fk : tableFieldValueDTO.getFk()) {
                            if(pkIdMap.containsKey(fk)) {
                                tableFieldValueDTO.getValueMap().put(fk, pkIdMap.get(fk));
                            } else {
                                tableFieldValueDTO.setSuccessInt(2);
                                tableFieldValueDTO.setErrorMsg("刷新外键异常!");
                            }
                        }
                    }
                }
            }

            return returnList;
        }

    }

    /**
     * 如果是一对多关系的更新操作，那么多方的数据部分需要进行删除，这里将多方数据删除后新增，这里维护对应需要删除的数据。
     *
     * @param tableFieldValueDTOList 生成的 TableFieldValueDTO 列表
     * @return 需要删除的数据描述
     * @throws Exception 刷新失败时抛出
     */
    public static DelDetailTableValueDTO<String> refreshTableFieldValueDTOListUpdate2Del(List<TableFieldValueDTO> tableFieldValueDTOList) throws Exception {
        DelDetailTableValueDTO<String> unNecessaryTableValueDTO = new DelDetailTableValueDTO<String>();

        List<TableFieldValueDTO> returnList = deepCopyList(tableFieldValueDTOList, TableFieldValueDTO.class);

        if(returnList != null && returnList.size() > 0) {
            returnList = returnList.stream().sorted(Comparator.comparing(l -> l.getFk() == null ? 0 : l.getFk().size(), Comparator.nullsFirst(Integer::compareTo))).collect(toList());

            for(TableFieldValueDTO tableFieldValueDTO : returnList) {
                if(tableFieldValueDTO.getFk() == null || tableFieldValueDTO.getFk().size() == 0) {
                    String pkValue = tableFieldValueDTO.getValueMap().get(tableFieldValueDTO.getPk()).toString();
                    unNecessaryTableValueDTO.setPk(tableFieldValueDTO.getPk());
                    unNecessaryTableValueDTO.setPkValue(pkValue);
                }
                if(tableFieldValueDTO.getFk() != null && tableFieldValueDTO.getFk().size() > 0) {
                    if(tableFieldValueDTO.getFk().contains(unNecessaryTableValueDTO.getPk())) {
                        List<String> tableNameList = unNecessaryTableValueDTO.getTableNameList();
                        if(tableNameList == null) {
                            tableNameList = new ArrayList<>();
                        }
                        if(!tableNameList.contains(tableFieldValueDTO.getTableName())) {
                            tableNameList.add(tableFieldValueDTO.getTableName());
                        }
                        unNecessaryTableValueDTO.setTableNameList(tableNameList);
                    }
                }
            }
        }

        return unNecessaryTableValueDTO;
    }

    /**
     * 删除操作时，维护对应需要删除的数据
     *
     * @param tableFieldValueDTOList 生成的 TableFieldValueDTO 列表
     * @return 需要删除的数据列表
     * @throws Exception 刷新失败时抛出
     */
    public static List<DelDetailTableValueDTO<String>> refreshTableFieldValueDTOListDelete(List<TableFieldValueDTO> tableFieldValueDTOList) throws Exception {
        List<DelDetailTableValueDTO<String>> unNecessaryTableValueDTOList = new ArrayList<DelDetailTableValueDTO<String>>();

        List<TableFieldValueDTO> returnList = deepCopyList(tableFieldValueDTOList, TableFieldValueDTO.class);

        if(returnList != null && returnList.size() > 0) {
            returnList = returnList.stream().sorted(Comparator.comparing(l -> l.getFk() == null ? 0 : l.getFk().size(), Comparator.nullsFirst(Integer::compareTo))).collect(toList());

            for(TableFieldValueDTO tableFieldValueDTO : returnList) {
                if(tableFieldValueDTO.getFk() == null || tableFieldValueDTO.getFk().size() == 0) {
                    if(tableFieldValueDTO.getValueMap().containsKey(tableFieldValueDTO.getPk())) {
                        DelDetailTableValueDTO<String> unNecessaryTableValueDTO = new DelDetailTableValueDTO<String>();
                        String pkValue = tableFieldValueDTO.getValueMap().get(tableFieldValueDTO.getPk()).toString();
                        unNecessaryTableValueDTO.setPk(tableFieldValueDTO.getPk());
                        unNecessaryTableValueDTO.setPkValue(pkValue);

                        List<String> tableNameList = unNecessaryTableValueDTO.getTableNameList();
                        if(tableNameList == null) {
                            tableNameList = new ArrayList<>();
                        }
                        if(!tableNameList.contains(tableFieldValueDTO.getTableName())) {
                            tableNameList.add(tableFieldValueDTO.getTableName());
                        }
                        unNecessaryTableValueDTO.setTableNameList(tableNameList);

                        unNecessaryTableValueDTOList.add(unNecessaryTableValueDTO);
                    }

                }
                if(tableFieldValueDTO.getFk() != null && tableFieldValueDTO.getFk().size() > 0) {
                    for(String fk : tableFieldValueDTO.getFk()) {
                        if(tableFieldValueDTO.getValueMap().containsKey(fk)) {
                            DelDetailTableValueDTO<String> unNecessaryTableValueDTO = new DelDetailTableValueDTO<String>();
                            String pkValue = tableFieldValueDTO.getValueMap().get(fk).toString();
                            unNecessaryTableValueDTO.setPk(fk);
                            unNecessaryTableValueDTO.setPkValue(pkValue);

                            List<String> tableNameList = unNecessaryTableValueDTO.getTableNameList();
                            if(tableNameList == null) {
                                tableNameList = new ArrayList<>();
                            }
                            if(!tableNameList.contains(tableFieldValueDTO.getTableName())) {
                                tableNameList.add(tableFieldValueDTO.getTableName());
                            }
                            unNecessaryTableValueDTO.setTableNameList(tableNameList);

                            unNecessaryTableValueDTOList.add(unNecessaryTableValueDTO);
                        } else if(tableFieldValueDTO.getValueMap().containsKey(tableFieldValueDTO.getPk())) {
                            DelDetailTableValueDTO<String> unNecessaryTableValueDTO = new DelDetailTableValueDTO<String>();
                            String pkValue = tableFieldValueDTO.getValueMap().get(tableFieldValueDTO.getPk()).toString();
                            unNecessaryTableValueDTO.setPk(tableFieldValueDTO.getPk());
                            unNecessaryTableValueDTO.setPkValue(pkValue);

                            List<String> tableNameList = unNecessaryTableValueDTO.getTableNameList();
                            if(tableNameList == null) {
                                tableNameList = new ArrayList<>();
                            }
                            if(!tableNameList.contains(tableFieldValueDTO.getTableName())) {
                                tableNameList.add(tableFieldValueDTO.getTableName());
                            }
                            unNecessaryTableValueDTO.setTableNameList(tableNameList);

                            unNecessaryTableValueDTOList.add(unNecessaryTableValueDTO);
                        }

                    }

                }
            }
        }

        return unNecessaryTableValueDTOList;
    }

    /**
     * 根据 业务 - 表结构 映射关系，维护对应的操作数据
     *
     * @param tableName       操作表名
     * @param mappingTableDTO 业务 - 表结构 映射关系
     * @param values          传递过来的数据值
     * @return 维护好的 TableFieldValueDTO
     */
    public static TableFieldValueDTO geneTableFieldValueDTO(String tableName, MappingTableDTO mappingTableDTO, Map<String, Object> values) {
        TableFieldValueDTO tableFieldValueDTO = new TableFieldValueDTO();

        Boolean checkBool = checkMapAllInFieldList(mappingTableDTO.getField(), values);
        if (checkBool) {
            tableFieldValueDTO.setTableName(tableName);
            tableFieldValueDTO.setPk(mappingTableDTO.getPk());
            if(mappingTableDTO.getField() != null && mappingTableDTO.getField().size() > 0) {
                Map<String, Object> filterValues = new HashMap<>();
                for(String field : mappingTableDTO.getField()) {
                    if(values.containsKey(field)) {
                        filterValues.put(field, values.get(field));
                    }
                }
                tableFieldValueDTO.setValueMap(filterValues);
            } else {
                tableFieldValueDTO.setValueMap(values);
            }
        } else {
            tableFieldValueDTO.setSuccessInt(1);
            tableFieldValueDTO.setErrorMsg("缺失字段!");
        }

        return tableFieldValueDTO;
    }

    /**
     * 如果存在外键设置，那么需要过滤一下平铺的map，把纯主表的map对象进行移除
     *
     * @param tableName        操作表名
     * @param mappingTableDTO  业务 - 表结构 映射关系
     * @param flattenedMapList 平铺后的 map 列表
     * @return 过滤后的 map 列表
     */
    public static List<Map<String, Object>> withFKFilter(String tableName, MappingTableDTO mappingTableDTO, List<Map<String, Object>> flattenedMapList) {
        List<Map<String, Object>> filterMapList = new ArrayList<>();

        for (Map<String, Object> flattenedMap : flattenedMapList) {
            Boolean b = checkMapAllInFieldList(mappingTableDTO.getField(), flattenedMap);
            if (b) {
                filterMapList.add(flattenedMap);
            }
        }

        return filterMapList;
    }

    /**
     * 数据完整性检测，业务字段数据和规则是否匹配
     *
     * @param fieldList 需要的字段列表
     * @param values    待检测的数据
     * @return 是否包含全部字段
     */
    public static Boolean checkMapAllInFieldList(List<String> fieldList, Map<String, Object> values) {
        Boolean checkFlag = true;
        if(fieldList != null && fieldList.size() > 0) {
            for (String field : fieldList) {
                if (!values.containsKey(field)) {
                    checkFlag = false;
                    break;
                }
            }
        }
        return checkFlag;
    }

    /**
     * List 深拷贝
     *
     * @param <T>          列表元素类型
     * @param originalList 原始列表
     * @param clazz        元素类型
     * @return 深拷贝后的列表
     * @throws IOException 序列化失败时抛出
     */
    public static <T> List<T> deepCopyList(List<T> originalList, Class<T> clazz) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // 序列化List为JSON字符串
        String jsonString = mapper.writeValueAsString(originalList);
        // 使用明确的类型信息反序列化JSON字符串为新的List
        return mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    /**
     * 使用 jackson 把 JSON 对象中特定节点的值都查询出来， 可用于参数校验、参数提取
     *
     * @param node      待查询的 JSON 节点
     * @param targetKey 目标字段名
     * @return 匹配到的字段值列表
     */
    public static List<String> extractKeyValues(JsonNode node, String targetKey) {
        List<String> values = new ArrayList<>();

        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode fieldValue = node.get(fieldName);

            if (fieldValue.isObject()) {
                values.addAll(extractKeyValues(fieldValue, targetKey));
            } else if (fieldValue.isArray()) {
                for (JsonNode element : fieldValue) {
                    values.addAll(extractKeyValues(element, targetKey));
                }
            } else if (fieldName.equals(targetKey)) {
                values.add(fieldValue.asText());
            }
        }
        return values;
    }

    /**
     * json 转平铺
     *
     * @param jsonNode 待平铺的 JSON 节点
     * @return 平铺后的键值 map 列表
     */
    public static List<Map<String, Object>> flattenJson(JsonNode jsonNode) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> currentMap = new LinkedHashMap<>();
        flattenJsonNode(jsonNode, currentMap, result);
        return result;
    }

    private static void flattenJsonNode(JsonNode jsonNode, Map<String, Object> currentMap, List<Map<String, Object>> result) {
        if (jsonNode.isObject()) {
            Map<String, Object> clonedMap = new LinkedHashMap<>(currentMap);
            jsonNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    value.forEach(item -> flattenJsonNode(item, clonedMap, result));
                } else if (value.isObject()) {
                    flattenJsonNode(value, clonedMap, result);
                } else {
                    clonedMap.put(key, value.asText());
                }
            });
            // 添加到结果中
            result.add(clonedMap);
        }
    }

}
