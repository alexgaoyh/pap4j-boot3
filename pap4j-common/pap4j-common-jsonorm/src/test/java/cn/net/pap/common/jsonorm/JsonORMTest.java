package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.dto.MappingDataDTO;
import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import cn.net.pap.common.jsonorm.dto.TableFieldValueDTO;
import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.List;
import java.util.Map;

public class JsonORMTest {

    /**
     * 读取 mapping orm
     * @throws Exception
     */
    @Test
    public void parseJsonMappingORMTest() throws Exception {
        System.out.println(getJSONMappingORM());
    }

    private List<MappingORMDTO> getJSONMappingORM() throws Exception {
        File file = ResourceUtils.getFile("classpath:json-mapping-orm.json");
        String json = JsonORMUtil.readFileToString(file);
        ObjectMapper mapper = new ObjectMapper();
        List<MappingORMDTO> mappingORMDTOList = mapper.readValue(json, new TypeReference<List<MappingORMDTO>>() {
        });
        return mappingORMDTOList;
    }

    /**
     * 读取 业务数据
     * @throws Exception
     */
    @Test
    public void parseJsonMappingDataTest() throws Exception {
        System.out.println(getJSONMappingData());
    }

    private MappingDataDTO getJSONMappingData() throws Exception {
        File file = ResourceUtils.getFile("classpath:C_001_001.json");
        String json = JsonORMUtil.readFileToString(file);
        ObjectMapper mapper = new ObjectMapper();

        MappingDataDTO mappingDataDTO = mapper.readValue(json, new TypeReference<MappingDataDTO>() {
        });
        return mappingDataDTO;
    }

    /**
     * JSON 数据转平铺
     * @throws Exception
     */
    @Test
    public void flattenJsonTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> data = getJSONMappingData().getData();
        for (JsonNode node : data) {
            String dataStr = mapper.writeValueAsString(node);
            JsonNode jsonNode2 = mapper.readTree(dataStr);
            List<Map<String, Object>> flattenedList2 = JsonORMUtil.flattenJson(jsonNode2);
            System.out.println(flattenedList2);
        }
    }

    /**
     * json 转平铺后，封装 list map 内具有相同属性的 map 对象
     * @throws Exception
     */
    @Test
    public void findUniqueKeyValuePairsTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> data = getJSONMappingData().getData();
        for (JsonNode node : data) {
            String dataStr = mapper.writeValueAsString(node);
            JsonNode jsonNode2 = mapper.readTree(dataStr);
            List<Map<String, Object>> flattenedList2 = JsonORMUtil.flattenJson(jsonNode2);

            Map<String, Object> uniqueKeyValuePairs = JsonORMUtil.findUniqueKeyValuePairs(flattenedList2);
            System.out.println(uniqueKeyValuePairs);

        }
    }

    /**
     * 读取 json-mapping-orm.json(业务-表结构映射关系) 和 C_001_001.json(业务数据) 两个文件，封装结构化的可供 DB 操作的对象。
     * @throws Exception
     */
    @Test
    public void geneForOperator() throws Exception {
        // 业务 - 表结构 映射关系
        MappingORMDTO mappingORMDTO = getJSONMappingORM().stream().filter(e -> e.getPapBussId().equals("C_001_001")).findFirst().get();
        // 业务 数据
        List<JsonNode> mappingDataDTO = getJSONMappingData().getData();

        for (JsonNode jsonNode : mappingDataDTO) {
            List<TableFieldValueDTO> tableFieldValueDTOList = JsonORMUtil.geneTableFieldValueDTOList(mappingORMDTO, jsonNode);
            System.out.println(tableFieldValueDTOList);
        }

    }


}
