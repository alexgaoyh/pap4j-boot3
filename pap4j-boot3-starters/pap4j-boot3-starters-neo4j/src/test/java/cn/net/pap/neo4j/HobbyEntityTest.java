package cn.net.pap.neo4j;

import cn.net.pap.neo4j.entity.HobbyEntity;
import cn.net.pap.neo4j.repository.HobbyRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.driver.internal.value.NodeValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Neo4jApplication.class})
public class HobbyEntityTest {

    @Autowired
    private HobbyRepository hobbyRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    // @Test
    public void inertTest() {
        HobbyEntity hobbyEntity1 = new HobbyEntity();
        hobbyEntity1.setHobbyId("H1");
        hobbyEntity1.setHobbyName("H1");
        List<String> propList1 = new ArrayList<>();
        propList1.add("list11");
        propList1.add("list12");
        hobbyEntity1.setPropList(propList1);
        Map<String, Object> propMap1 = new HashMap<>();
        propMap1.put("mapKey11", "mapValue11");
        propMap1.put("mapKey12", "mapValue12");
        hobbyEntity1.setPropMap(propMap1);
        hobbyRepository.save(hobbyEntity1);

        HobbyEntity hobbyEntity2 = new HobbyEntity();
        hobbyEntity2.setHobbyId("H2");
        hobbyEntity2.setHobbyName("H2");
        List<String> propList2 = new ArrayList<>();
        propList2.add("list21");
        propList2.add("list122");
        hobbyEntity2.setPropList(propList2);
        Map<String, Object> propMap2 = new HashMap<>();
        propMap2.put("mapKey21", "mapValue21");
        propMap2.put("mapKey22", "mapValue22");
        hobbyEntity2.setPropMap(propMap2);
        hobbyRepository.save(hobbyEntity2);

    }

    // @Test
    public void findAllTest() {
        List<HobbyEntity> findAll = hobbyRepository.findAll();
        System.out.println(findAll);
    }

    // @Test
    public void findByQueryParamsTest() {
        HobbyEntity queryParams = new HobbyEntity();
        //queryParams.setHobbyName("H1");

        StringBuilder query = new StringBuilder("MATCH (p:hobby) WHERE 1 = 1 ");

        Map<String, Object> parameters = new HashMap<>();

        if (queryParams.getHobbyId() != null) {
            query.append(" AND p.hobbyId =~ $hobbyId ");
            parameters.put("hobbyId", queryParams.getHobbyId());
        }

        if (queryParams.getHobbyName() != null) {
            query.append(" AND p.hobbyName =~ $hobbyName ");
            parameters.put("hobbyName", queryParams.getHobbyName());
        }

        // 分页 排序
        query.append(" RETURN p").append(" ORDER BY p.hobbyName ASC ").append(" SKIP ").append(0).append(" LIMIT ").append(2);

        Collection<HobbyEntity> all = neo4jClient.query(query.toString())
                .bindAll(parameters)
                .fetchAs(HobbyEntity.class)
                .mappedBy((typeSystem, record) -> {
                    HobbyEntity entity = new HobbyEntity();
                    entity.setHobbyId(record.get("p").get("hobbyId").asString());
                    entity.setHobbyName(record.get("p").get("hobbyName").asString());

                    List<String> propList = new ArrayList<>();
                    ((NodeValue)record.get("p")).asNode().labels().forEach((label) -> propList.add(label));
                    entity.setPropList(propList);

                    Map<String, Object> propMap = new HashMap<>();
                    (((NodeValue)record.get("p")).asNode().asMap()).forEach((key, value) -> {
                        if (key.startsWith("propMap.")) {
                            propMap.put(key.substring("propMap.".length()), value);
                        }
                    });
                    entity.setPropMap(propMap);

                    return entity;
                })
                .all();

        System.out.println(all);

    }

}
