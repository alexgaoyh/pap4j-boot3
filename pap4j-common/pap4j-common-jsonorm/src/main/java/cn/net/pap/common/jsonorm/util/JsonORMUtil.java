package cn.net.pap.common.jsonorm.util;

import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import cn.net.pap.common.jsonorm.dto.MappingTableDTO;
import cn.net.pap.common.jsonorm.dto.TableFieldValueDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * JSON ORM 工具类
 */
public class JsonORMUtil {

    /**
     * 文档读取 - 读取文本内容
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static String readFileToString(File file) throws IOException {
        String JSON = "";
        InputStream stream = new FileInputStream(file);
        String nextString = "";
        try {
            if (stream != null) {
                InputStreamReader streamReader = new InputStreamReader(stream, "UTF-8");
                BufferedReader reader = new BufferedReader(streamReader);
                while ((nextString = reader.readLine()) != null)
                    JSON = new StringBuilder().append(JSON).append(nextString).toString();
            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
        return JSON;
    }

    /**
     * 从 map list 里面进行遍历取值，把 多个集合中具有相同key和value不分的数据查出来，放到一个单独的 map 里面返回，
     * 入参： [{key1=value1, key2=value2}, {key1=value1, key2=value2, key3=value3}, {key1=value1}]
     * 出参： {key1=value1}
     *
     * @param mapList
     * @return
     */
    public static Map<String, Object> findUniqueKeyValuePairs(List<Map<String, Object>> mapList) {
        Map<String, Object> returnMap = new LinkedHashMap<>();

        // 计算每个key对应的value出现的次数
        Map<String, List<Object>> keyValueCounts = new HashMap<>();
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
     * @param mappingORMDTO
     * @param jsonNode
     * @return
     * @throws Exception
     */
    public static List<TableFieldValueDTO> geneTableFieldValueDTOList(MappingORMDTO mappingORMDTO, JsonNode jsonNode) throws Exception {
        List<TableFieldValueDTO> tableFieldValueDTOList = new ArrayList<>();

        Map<String, MappingTableDTO> mappingMap = mappingORMDTO.getMapping();

        for (Map.Entry<String, MappingTableDTO> entry : mappingMap.entrySet()) {
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

        return tableFieldValueDTOList;
    }

    /**
     * 刷新生成的 TableFieldValueDTO 对象，根据主键和外键标识，将主键和外键的关联关系添加进来。
     * @param tableFieldValueDTOList
     * @return
     * @throws Exception
     */
    public static List<TableFieldValueDTO> refreshTableFieldValueDTOList(List<TableFieldValueDTO> tableFieldValueDTOList) throws Exception {
        if(tableFieldValueDTOList != null && tableFieldValueDTOList.size() > 0) {
            tableFieldValueDTOList = tableFieldValueDTOList.stream().sorted(Comparator.comparing(l -> l.getFk() == null ? 0 : l.getFk().size(), Comparator.nullsFirst(Integer::compareTo))).collect(toList());
            Map<String, String> pkIdMap = new HashMap<>();
            for(TableFieldValueDTO tableFieldValueDTO : tableFieldValueDTOList) {
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

        return tableFieldValueDTOList;
    }

    /**
     * 根据 业务 - 表结构 映射关系，维护对应的操作数据
     *
     * @param tableName       操作表名
     * @param mappingTableDTO 业务 - 表结构 映射关系
     * @param values          传递过来的数据值
     * @return
     */
    public static TableFieldValueDTO geneTableFieldValueDTO(String tableName, MappingTableDTO mappingTableDTO, Map<String, Object> values) {
        TableFieldValueDTO tableFieldValueDTO = new TableFieldValueDTO();

        Boolean checkBool = checkMapAllInFieldList(mappingTableDTO.getField(), values);
        if (checkBool) {
            tableFieldValueDTO.setTableName(tableName);
            tableFieldValueDTO.setPk(mappingTableDTO.getPk());
            tableFieldValueDTO.setValueMap(values);
        } else {
            tableFieldValueDTO.setSuccessInt(1);
            tableFieldValueDTO.setErrorMsg("缺失字段!");
        }

        return tableFieldValueDTO;
    }

    /**
     * 如果存在外键设置，那么需要过滤一下平铺的map，把纯主表的map对象进行移除
     *
     * @param tableName
     * @param mappingTableDTO
     * @param flattenedMapList
     * @return
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
     * @param fieldList
     * @param values
     * @return
     */
    public static Boolean checkMapAllInFieldList(List<String> fieldList, Map<String, Object> values) {
        Boolean checkFlag = true;
        for (String field : fieldList) {
            if (!values.containsKey(field)) {
                checkFlag = false;
                break;
            }
        }
        return checkFlag;
    }

    /**
     * json 转平铺
     *
     * @param jsonNode
     * @return
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
