package cn.net.pap.neo4j;

import cn.net.pap.neo4j.dto.HLMNodeWithTypeDTO;
import cn.net.pap.neo4j.dto.HLMListDTO;
import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.entity.HLMRelationshipEntity;
import cn.net.pap.neo4j.repository.HLMRelationshipRepository;
import cn.net.pap.neo4j.repository.HLMRepository;
import cn.net.pap.neo4j.util.kg.HLMEntity2KGConvert;
import cn.net.pap.neo4j.util.kg.PathValue2KGConvert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.value.PathValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Neo4jApplication.class})
public class HLMEntityTest {

    @Autowired
    private HLMRepository hlmRepository;

    @Autowired
    private HLMRelationshipRepository hlmRelationshipRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @Test
    public void findByName() {
        List<HLMEntity> jm = hlmRepository.findByName("贾母");
        System.out.println(jm);
        Map<String, Object> kgGraph = HLMEntity2KGConvert.convertToKnowledgeGraph(jm.get(0));
        System.out.println(kgGraph);
    }

    @Test
    public void findByStartNodeName() {
        List<HLMNodeWithTypeDTO> jby = hlmRelationshipRepository.findByStartNodeName("贾宝玉");
        System.out.println(jby);
    }

    @Test
    public void getShortestPathBetweenNodesByName() {
        List<List<PathValue>> shortestPath = hlmRelationshipRepository.getShortestPathBetweenNodesByName("贾宝玉", "林黛玉");
        System.out.println(shortestPath);
        Map<String, Object> kgGraph = PathValue2KGConvert.convertToKnowledgeGraph(shortestPath);
        System.out.println(kgGraph);
    }

    @Test
    public void getById() {
        HLMEntity byId = hlmRepository.getById(157L);
        System.out.println(byId);
    }

    @Test
    public void queryDTO(){
        String cypherQuery = "MATCH (h:HLM)-[r]->(m:HLM) WHERE id(h)="+157+" RETURN h.name AS name, COLLECT({type: r.type, endNode: m}) AS relationships";
        List<HLMEntity> results = neo4jClient.query(cypherQuery)
                .fetchAs(HLMEntity.class)
                .mappedBy((typeSystem, record) -> {
                    HLMEntity dto = new HLMEntity();
                    dto.setName(record.get("name").asString());

                    Set<HLMRelationshipEntity> relationshipEntityList = new HashSet<>();
                    List<Object> relationships = record.get("relationships").asList();
                    if(relationships != null && relationships.size() > 0) {
                        for(Object object : relationships) {
                            HLMRelationshipEntity hlmRelationshipEntity = new HLMRelationshipEntity();
                            Object typeObj = ((Map) object).get("type");
                            hlmRelationshipEntity.setType(typeObj.toString());
                            Object endNodeObj = ((Map) object).get("endNode");
                            if(endNodeObj instanceof InternalNode) {
                                InternalNode internalNode = (InternalNode)endNodeObj;
                                HLMEntity hlmEntity = new HLMEntity();
                                hlmEntity.setName(internalNode.asMap().get("name").toString());
                                hlmRelationshipEntity.setEndNode(hlmEntity);
                            }
                            relationshipEntityList.add(hlmRelationshipEntity);
                        }
                    }
                    dto.setRelationships(relationshipEntityList);
                    return dto;
                }).all().stream().collect(Collectors.toList());
        System.out.println(results);
    }

    @Test
    public void getRoot() {
        List<HLMEntity> rootList = hlmRepository.getRoot();
        System.out.println(rootList);
    }

    @Test
    public void getLeaf() {
        List<HLMEntity> leafList = hlmRepository.getLeaf();
        System.out.println(leafList);
    }

    @Test
    public void getDistinctRelationshipType() {
        List<String> relationshipTypeList = hlmRelationshipRepository.getDistinctRelationshipType();
        System.out.println(relationshipTypeList);
    }

    @Test
    public void getCycle() {
        List<HLMEntity> leafList = hlmRepository.getCycle();
        System.out.println(leafList);
    }

    @Test
    public void getCycle2(){
        String cypherQuery = "MATCH path = (n:HLM)-[*]->(n:HLM) RETURN DISTINCT nodes(path) AS loopNodes";
        List<HLMListDTO> results = neo4jClient.query(cypherQuery)
                .fetchAs(HLMListDTO.class)
                .mappedBy((typeSystem, record) -> {
                    HLMListDTO HLMListDTO = new HLMListDTO();
                    List<HLMEntity> cycleList = new ArrayList<>();
                    List<Object> loopNodes = record.get("loopNodes").asList();
                    for(Object object : loopNodes) {
                        if(object instanceof InternalNode) {
                            InternalNode internalNode = (InternalNode)object;
                            HLMEntity hlmEntity = new HLMEntity();
                            hlmEntity.setName(internalNode.asMap().get("name").toString());
                            cycleList.add(hlmEntity);
                        }
                    }
                    HLMListDTO.setDetails(cycleList);
                    return HLMListDTO;
                }).all().stream().collect(Collectors.toList());
        System.out.println(results);
        List<HLMListDTO> distinctResults = HLMListDTO.distinct(results);
        System.out.println(distinctResults);
    }

}
