package cn.net.pap.neo4j;

import cn.net.pap.neo4j.deserializer.jackson.DepartmentEntityDeserializer;
import cn.net.pap.neo4j.entity.DepartmentEntity;
import cn.net.pap.neo4j.repository.DepartmentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {Neo4jApplication.class})
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
public class DepartmentEntityTest {

    private final DepartmentRepository departmentRepository;
    private final Neo4jClient neo4jClient;

    public DepartmentEntityTest(DepartmentRepository departmentRepository, Neo4jClient neo4jClient) {
        this.departmentRepository = departmentRepository;
        this.neo4jClient = neo4jClient;
    }

    // @Test
    public void initData() throws Exception {
        // 这里的数据可以从 pap4j-common/pap4j-common-excel/cn.net.pap.common.excel.ExcelUtilTest.parentChild() 这里解析出来
        // 整体流程仿照 从 xls 解析数据，然后序列化/反序列化传输数据，然后存储至图数据库.
        String initJson = "[{\"remark\":\"A\",\"child\":[{\"remark\":\"A01\",\"child\":[{\"remark\":\"A0101\",\"child\":[{\"remark\":\"A010101\",\"parent\":{\"remark\":\"A0101\"}},{\"remark\":\"A010102\",\"parent\":{\"remark\":\"A0101\"}},{\"remark\":\"A010103\",\"parent\":{\"remark\":\"A0101\"}},{\"remark\":\"A010104\",\"parent\":{\"remark\":\"A0101\"}}],\"parent\":{\"remark\":\"A01\"}},{\"remark\":\"A0102\",\"child\":[{\"remark\":\"A010201\",\"parent\":{\"remark\":\"A0102\"}},{\"remark\":\"A010202\",\"parent\":{\"remark\":\"A0102\"}},{\"remark\":\"A010203\",\"parent\":{\"remark\":\"A0102\"}},{\"remark\":\"A010204\",\"parent\":{\"remark\":\"A0102\"}}],\"parent\":{\"remark\":\"A01\"}}],\"parent\":{\"remark\":\"A\"}},{\"remark\":\"A02\",\"child\":[{\"remark\":\"A0201\",\"child\":[{\"remark\":\"A020101\",\"parent\":{\"remark\":\"A0201\"}},{\"remark\":\"A020102\",\"parent\":{\"remark\":\"A0201\"}}],\"parent\":{\"remark\":\"A02\"}},{\"remark\":\"A0202\",\"child\":[{\"remark\":\"A020201\",\"parent\":{\"remark\":\"A0202\"}}],\"parent\":{\"remark\":\"A02\"}}],\"parent\":{\"remark\":\"A\"}}]}]";

        ObjectMapper objectMapperDeserializer = new ObjectMapper();
        SimpleModule module2 = new SimpleModule();
        module2.addDeserializer(DepartmentEntity.class, new DepartmentEntityDeserializer());
        objectMapperDeserializer.registerModule(module2);
        List<DepartmentEntity> departmentEntities = objectMapperDeserializer.readValue(initJson, new TypeReference<List<DepartmentEntity>>() {});

        departmentRepository.saveAll(departmentEntities);

    }

    // @Test
    public void getBrothersByRemark() throws Exception {
        List<DepartmentEntity> A0101 = departmentRepository.getBrothersByRemark("A0101");
        List<DepartmentEntity> A010101 = departmentRepository.getBrothersByRemark("A010101");
        assertTrue(A0101 != null && A010101 != null);
    }


    // @Test
    public void getMaxOutDegree(){
        // 最大 出度 节点
        String cypherQuery = "MATCH (n) OPTIONAL MATCH (n)-[r]->() WITH n, COUNT(r) AS outDegree ORDER BY outDegree DESC LIMIT 1 RETURN n";
        List<DepartmentEntity> results = neo4jClient.query(cypherQuery)
                .fetchAs(DepartmentEntity.class)
                .mappedBy((typeSystem, record) -> {
                    DepartmentEntity departmentEntity = new DepartmentEntity();
                    departmentEntity.setRemark(record.values().get(0).get("remark").asString());
                    return departmentEntity;
                }).all().stream().collect(Collectors.toList());
        System.out.println(results);
    }


    /**
     * 子图同构的一种编写实现，在 match where 中把关系指定出来，之后返回符合条件的节点
     */
    // @Test
    public void getTargetGraph(){
        // a[:child]->b[:child]->c， c 没有 child
        String cypherQuery = "MATCH (a:department)-[:child]->(b:department), (b:department)-[:child]->(c:department) WHERE NOT (c:department)-[:child]->() RETURN a, b, c;";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("a", record.values().get(0).get("remark").toString());
                    map.put("b", record.values().get(1).get("remark").toString());
                    map.put("c", record.values().get(2).get("remark").toString());
                    return map;
                }).all().stream().collect(Collectors.toList());
        System.out.println(results);
    }
}
