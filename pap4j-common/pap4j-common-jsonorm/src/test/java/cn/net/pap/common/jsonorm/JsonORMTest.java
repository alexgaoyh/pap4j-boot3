package cn.net.pap.common.jsonorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.jsonorm.dto.DelDetailTableValueDTO;
import cn.net.pap.common.jsonorm.dto.MappingDataDTO;
import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import cn.net.pap.common.jsonorm.dto.TableFieldValueDTO;
import cn.net.pap.common.jsonorm.serializer.TableFieldValueDTOSerializer;
import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import cn.net.pap.common.jsonorm.util.SqlUtil;
import cn.net.pap.common.jsonorm.util.ValidateUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JsonORMTest {
    private static final Logger log = LoggerFactory.getLogger(JsonORMTest.class);

    /**
     * 读取 mapping orm
     * @throws Exception
     */
    @Test
    public void parseJsonMappingORMTest() throws Exception {
        log.info("{}", getJSONMappingORM("json-mapping-orm-C_001_001.json"));
    }

    private List<MappingORMDTO> getJSONMappingORM(String ormJson) throws Exception {
        File file = ResourceUtils.getFile("classpath:" + ormJson);
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
        log.info("{}", getJSONMappingData("C_001_001.json"));
    }

    private MappingDataDTO getJSONMappingData(String datajSON) throws Exception {
        File file = ResourceUtils.getFile("classpath:" + datajSON);
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
        List<JsonNode> data = getJSONMappingData("C_001_001.json").getData();
        for (JsonNode node : data) {
            String dataStr = mapper.writeValueAsString(node);
            JsonNode jsonNode2 = mapper.readTree(dataStr);
            List<Map<String, Object>> flattenedList2 = JsonORMUtil.flattenJson(jsonNode2);
            log.info("{}", flattenedList2);
        }
    }

    /**
     * json 转平铺后，封装 list map 内具有相同属性的 map 对象
     * @throws Exception
     */
    @Test
    public void findUniqueKeyValuePairsTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> data = getJSONMappingData("C_001_001.json").getData();
        for (JsonNode node : data) {
            String dataStr = mapper.writeValueAsString(node);
            JsonNode jsonNode2 = mapper.readTree(dataStr);
            List<Map<String, Object>> flattenedList2 = JsonORMUtil.flattenJson(jsonNode2);

            Map<String, Object> uniqueKeyValuePairs = JsonORMUtil.findUniqueKeyValuePairs(flattenedList2);
            log.info("{}", uniqueKeyValuePairs);

        }
    }

    /**
     * 读取 json-mapping-orm-C_001_001.json(业务-表结构映射关系) 和 C_001_001.json(业务数据) 两个文件，封装结构化的可供 DB 操作的对象。
     * @throws Exception
     */
    @Test
    public void geneForOperatorInsert() throws Exception {
        // 业务 - 表结构 映射关系
        MappingORMDTO mappingORMDTO = getJSONMappingORM("json-mapping-orm-C_001_001.json").stream().filter(e -> e.getPapBussId().equals("C_001_001")).findFirst().get();
        // 业务 数据
        List<JsonNode> mappingDataDTO = getJSONMappingData("C_001_001.json").getData();

        for (JsonNode jsonNode : mappingDataDTO) {
            List<TableFieldValueDTO> tableFieldValueDTOList = JsonORMUtil.geneTableFieldValueDTOList(mappingORMDTO, jsonNode);
            // log.info(tableFieldValueDTOList);

            if(mappingORMDTO.getOperator().equals("insert")) {
                List<TableFieldValueDTO> tableFieldValueDTOListRefresh = JsonORMUtil.refreshTableFieldValueDTOListInsert(tableFieldValueDTOList);
//                ObjectMapper mapper = new ObjectMapper();
//                SimpleModule module = new SimpleModule();
//                module.addSerializer(TableFieldValueDTO.class, new TableFieldValueDTOSerializer(Arrays.asList("tableName", "pk", "valueMap")));
//                mapper.registerModule(module);
//                log.info(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tableFieldValueDTOListRefresh));
                for(TableFieldValueDTO tableFieldValueDTO : tableFieldValueDTOListRefresh) {
                    log.info("{}", SqlUtil.geneInsertStatement(tableFieldValueDTO.getTableName(), tableFieldValueDTO.getValueMap()));
                }
            }
        }

    }


    @Test
    public void geneForOperatorUpdate2Del() throws Exception {
        // 业务 - 表结构 映射关系
        MappingORMDTO mappingORMDTO = getJSONMappingORM("json-mapping-orm-C_001_002.json").stream().filter(e -> e.getPapBussId().equals("C_001_002")).findFirst().get();
        // 业务 数据
        List<JsonNode> mappingDataDTO = getJSONMappingData("C_001_002.json").getData();

        for (JsonNode jsonNode : mappingDataDTO) {
            List<TableFieldValueDTO> tableFieldValueDTOList = JsonORMUtil.geneTableFieldValueDTOList(mappingORMDTO, jsonNode);

            if(mappingORMDTO.getOperator().equals("update")) {
//                DelDetailTableValueDTO<String> tableFieldValueDTOListUpdate2Del = JsonORMUtil.refreshTableFieldValueDTOListUpdate2Del(tableFieldValueDTOList);
//                log.info(SqlUtil.generateDeleteStatement(tableFieldValueDTOListUpdate2Del.getTableNameList().get(0),
//                        tableFieldValueDTOListUpdate2Del.getPk(), tableFieldValueDTOListUpdate2Del.getPkValue()));
                List<DelDetailTableValueDTO<String>> delDetailTableValueDTOS = JsonORMUtil.refreshTableFieldValueDTOListDelete(tableFieldValueDTOList);
                for(DelDetailTableValueDTO<String> delDetailTableValueDTO : delDetailTableValueDTOS) {
                    log.info("{}", SqlUtil.generateDeleteStatement(delDetailTableValueDTO.getTableNameList().get(0),
                            delDetailTableValueDTO.getPk(), delDetailTableValueDTO.getPkValue()));
                }

                List<TableFieldValueDTO> tableFieldValueDTOListInsert = JsonORMUtil.refreshTableFieldValueDTOListInsert(tableFieldValueDTOList, true);
                for(TableFieldValueDTO tableFieldValueDTOInsert : tableFieldValueDTOListInsert) {
                    log.info("{}", SqlUtil.geneInsertStatement(tableFieldValueDTOInsert.getTableName(),tableFieldValueDTOInsert.getValueMap()));
                }
            }
        }

    }

    @Test
    public void validateTest() throws Exception {
        // 业务 数据
        List<JsonNode> mappingDataDTO = getJSONMappingData("C_001_001.json").getData();

        for (JsonNode jsonNode : mappingDataDTO) {
            List<String> studentAge = JsonORMUtil.extractKeyValues(jsonNode, "student_age");
            for(String age : studentAge) {
                boolean numberBool = ValidateUtil.isNumber(age);
                log.info("{}", age + " : " + numberBool);
            }
        }
    }

    @Test
    public void geneForOperatorDelete() throws Exception {
        // 业务 - 表结构 映射关系
        MappingORMDTO mappingORMDTO = getJSONMappingORM("json-mapping-orm-C_001_003.json").stream().filter(e -> e.getPapBussId().equals("C_001_003")).findFirst().get();
        // 业务 数据
        List<JsonNode> mappingDataDTO = getJSONMappingData("C_001_003.json").getData();

        for (JsonNode jsonNode : mappingDataDTO) {
            List<TableFieldValueDTO> tableFieldValueDTOList = JsonORMUtil.geneTableFieldValueDTOList(mappingORMDTO, jsonNode);

            if(mappingORMDTO.getOperator().equals("delete")) {
                List<DelDetailTableValueDTO<String>> delDetailTableValueDTOS = JsonORMUtil.refreshTableFieldValueDTOListDelete(tableFieldValueDTOList);
                // log.info(delDetailTableValueDTOS);
                for(DelDetailTableValueDTO<String> delDetailTableValueDTO : delDetailTableValueDTOS) {
                    log.info("{}", SqlUtil.generateDeleteStatement(delDetailTableValueDTO.getTableNameList().get(0),
                            delDetailTableValueDTO.getPk(), delDetailTableValueDTO.getPkValue()));
                }
            }
        }

    }

    @Test
    public void geneForOperatorSelect() throws Exception {
        // 业务 - 表结构 映射关系
        MappingORMDTO mappingORMDTO = getJSONMappingORM("json-mapping-orm-C_001_004.json").stream().filter(e -> e.getPapBussId().equals("C_001_004")).findFirst().get();
        // 业务 数据
        List<JsonNode> mappingDataDTO = getJSONMappingData("C_001_004.json").getData();

        for (JsonNode jsonNode : mappingDataDTO) {
            List<TableFieldValueDTO> tableFieldValueDTOList = JsonORMUtil.geneTableFieldValueDTOList(mappingORMDTO, jsonNode);

            if(mappingORMDTO.getOperator().equals("select")) {
                for(TableFieldValueDTO tableFieldValueDTO : tableFieldValueDTOList) {
                    log.info("{}", SqlUtil.generateSelectStatement(tableFieldValueDTO.getTableName(),
                            tableFieldValueDTO.getFk(), tableFieldValueDTO.getValueMap()));
                }
            }
        }
    }

}
