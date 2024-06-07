package cn.net.pap.neo4j;

import cn.net.pap.neo4j.dto.HLMNodeWithTypeDTO;
import cn.net.pap.neo4j.dto.HLMListDTO;
import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.entity.HLMRelationshipEntity;
import cn.net.pap.neo4j.repository.HLMRelationshipRepository;
import cn.net.pap.neo4j.repository.HLMRepository;
import cn.net.pap.neo4j.util.kg.HLMEntity2KGConvert;
import cn.net.pap.neo4j.util.kg.HLMListDTO2KGConvert;
import cn.net.pap.neo4j.util.kg.PathValue2KGConvert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.summary.ResultSummary;
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
        String cypherQuery = "MATCH path = (n:HLM)-[*]->(n:HLM) RETURN DISTINCT nodes(path) AS loopNodes, relationships(path) AS loopRelationships";
        List<HLMListDTO> results = neo4jClient.query(cypherQuery)
                .fetchAs(HLMListDTO.class)
                .mappedBy((typeSystem, record) -> {
                    HLMListDTO hlmListDTO = new HLMListDTO();
                    List<HLMEntity> cycleList = new ArrayList<>();
                    Map<Long, String> elementIdAndNameMap = new HashMap<>();
                    List<Object> loopNodes = record.get("loopNodes").asList();
                    for(Object object : loopNodes) {
                        if(object instanceof InternalNode) {
                            InternalNode internalNode = (InternalNode)object;
                            HLMEntity hlmEntity = new HLMEntity();
                            hlmEntity.setName(internalNode.asMap().get("name").toString());
                            elementIdAndNameMap.put(internalNode.id(), internalNode.asMap().get("name").toString());
                            cycleList.add(hlmEntity);
                        }
                    }
                    hlmListDTO.setDetails(cycleList);

                    List<HLMRelationshipEntity> relationshipList = new ArrayList<>();
                    List<Object> loopRelationships = record.get("loopRelationships").asList();
                    for(Object object : loopRelationships) {
                        if(object instanceof InternalRelationship) {
                            InternalRelationship internalRelationship = (InternalRelationship)object;
                            HLMRelationshipEntity hlmRelationshipEntity = new HLMRelationshipEntity();

                            long startNodeId = internalRelationship.startNodeId();
                            String startNodeName = elementIdAndNameMap.get(startNodeId);
                            HLMEntity startHLMEntity = new HLMEntity();
                            startHLMEntity.setName(startNodeName);

                            long endNodeId = internalRelationship.endNodeId();
                            String endNodeName = elementIdAndNameMap.get(endNodeId);
                            HLMEntity endHLMEntity = new HLMEntity();
                            endHLMEntity.setName(endNodeName);

                            hlmRelationshipEntity.setStartNode(startHLMEntity);
                            hlmRelationshipEntity.setEndNode(endHLMEntity);
                            hlmRelationshipEntity.setType(internalRelationship.asMap().get("type").toString());
                            relationshipList.add(hlmRelationshipEntity);
                        }
                    }
                    hlmListDTO.setRelations(relationshipList);
                    return hlmListDTO;
                }).all().stream().collect(Collectors.toList());
        System.out.println(results);
        List<HLMListDTO> distinctResults = HLMListDTO.distinct(results);
        System.out.println(distinctResults);
        Map<String, Object> kgGraph = HLMListDTO2KGConvert.convertToKnowledgeGraph(distinctResults);
        System.out.println(kgGraph);
    }

    /**
     * 指定两个节点[荣国府、贾宝玉] 两者之间的 jaccard 相似度
     */
    @Test
    public void getJaccard() {
        String cypherQuery = "MATCH (p1:HLM {name: '荣国府'})-[:RELATIONSHIP]->(p11) " +
                "WITH p1, collect(id(p11)) AS p1Cuisine " +
                "MATCH (p2:HLM {name: \"贾宝玉\"})-[:RELATIONSHIP]->(p22) " +
                "WITH p1, p1Cuisine, p2, collect(id(p22)) AS p2Cuisine " +
                "RETURN p1.name AS from, " +
                "p2.name AS to, " +
                "algo.similarity.jaccard(p1Cuisine, p2Cuisine) AS similarity";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> jaccardMap = new HashMap<>();
                    jaccardMap.put("from", record.get("from"));
                    jaccardMap.put("to", record.get("to"));
                    jaccardMap.put("similarity", record.get("similarity"));
                    return jaccardMap;
                }).all().stream().toList();
        System.out.println(results);
    }

    /**
     * 指定节点 [荣国府] 与其他节点的 jaccard 相似度
     */
    @Test
    public void getJaccard2() {
        String cypherQuery = "MATCH (p1:HLM {name: '荣国府'})-[:RELATIONSHIP]-(p11) " +
                "WITH p1, collect(id(p11)) AS p1Cuisine " +
                "MATCH (p2:HLM)-[:RELATIONSHIP]-(p22) where p2.name <> '荣国府' " +
                "WITH p1, p1Cuisine, p2, collect(id(p22)) AS p2Cuisine " +
                "RETURN p1.name AS from, " +
                "p2.name AS to, " +
                "algo.similarity.jaccard(p1Cuisine, p2Cuisine) AS similarity order by similarity desc";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> jaccardMap = new HashMap<>();
                    jaccardMap.put("from", record.get("from"));
                    jaccardMap.put("to", record.get("to"));
                    jaccardMap.put("similarity", record.get("similarity"));
                    return jaccardMap;
                }).all().stream().toList();
        System.out.println(results);
    }

    /**
     * 指定属性文本的 LevenshteinSimilarity
     * apoc-3.5.0.5-all.jar、 graph-algorithms-algo-3.5.4.0.jar
     * dbms.security.procedures.unrestricted=algo.\*,apoc.\*
     */
    @Test
    public void getTextLevenshteinSimilarity() {
        String cypherQuery = "MATCH (user1:HLM{name: \"林黛玉\"}), (user2:HLM) " +
                "WHERE user1 <> user2 " +
                "RETURN user2.name as name, apoc.text.levenshteinSimilarity(user1.name, user2.name) AS similarity " +
                "ORDER BY similarity DESC " +
                "LIMIT 10";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> jaccardMap = new HashMap<>();
                    jaccardMap.put("name", record.get("name"));
                    jaccardMap.put("similarity", record.get("similarity"));
                    return jaccardMap;
                }).all().stream().toList();
        System.out.println(results);
    }

    /**
     * 紧密中心度
     */
    @Test
    public void getClosenessCentrality() {
        String cypherQuery = "CALL algo.closeness.stream(\"HLM\", \"RELATIONSHIP\") " +
                "YIELD nodeId, centrality " +
                "MATCH (n:HLM) WHERE id(n) = nodeId " +
                "RETURN n.name AS node, centrality " +
                "ORDER BY centrality DESC " +
                "LIMIT 20;";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> centerMap = new HashMap<>();
                    centerMap.put("node", record.get("node"));
                    centerMap.put("centrality", record.get("centrality"));
                    return centerMap;
                }).all().stream().toList();
        System.out.println(results);
    }

    /**
     * 度中心度
     */
    @Test
    public void getDegreeCentrality() {
        // 这里的 direction 可以有三种参数：Both incoming outgoing
        String cypherQuery = "CALL algo.degree.stream(\"HLM\", \"RELATIONSHIP\", {direction: \"Both\"}) " +
                "YIELD nodeId, score " +
                "RETURN algo.asNode(nodeId).name AS name, score AS degree " +
                "ORDER BY degree DESC";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> centerMap = new HashMap<>();
                    centerMap.put("name", record.get("name"));
                    centerMap.put("degree", record.get("degree"));
                    return centerMap;
                }).all().stream().toList();
        System.out.println(results);
    }

    /**
     * 写入 度中心度
     */
    @Test
    public void setDegreeCentrality() {
        // 这里的 direction 可以有三种参数：Both incoming outgoing
        String cypherQuery = "CALL algo.degree.stream(\"HLM\", \"RELATIONSHIP\", {direction: \"Both\"}) " +
                "YIELD nodeId, score " +
                "WITH collect({name: algo.asNode(nodeId).name, degree: score}) AS degrees " +
                "UNWIND degrees AS degreeRecord " +
                "MERGE (n:HLM {name: degreeRecord.name}) " +
                "ON CREATE SET n._degree = degreeRecord.degree " +
                "ON MATCH SET n._degree = degreeRecord.degree ";
        ResultSummary run = neo4jClient.query(cypherQuery).run();
        System.out.println(run.counters().propertiesSet());
    }

    /**
     * 中介中心度
     */
    @Test
    public void getBetweenessCentrality() {
        String cypherQuery = "MATCH (c:HLM) " +
                "WITH collect(c) as characters " +
                "CALL algo.betweenness.stream(\"HLM\", \"RELATIONSHIP\") " +
                "YIELD nodeId, centrality " +
                "MATCH (c) WHERE id(c) = nodeId " +
                "RETURN c.name as name, centrality " +
                "ORDER BY centrality desc";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> centerMap = new HashMap<>();
                    centerMap.put("name", record.get("name"));
                    centerMap.put("centrality", record.get("centrality"));
                    return centerMap;
                }).all().stream().toList();
        System.out.println(results);
    }

    /**
     * 特征向量中心度
     */
    @Test
    public void getEigenVectorCentrality() {
        String cypherQuery = "CALL algo.pageRank.stream('HLM', 'RELATIONSHIP', {iterations:20, dampingFactor:0.85}) " +
                "YIELD nodeId, score " +
                "RETURN algo.asNode(nodeId).name AS name, score " +
                "ORDER BY score DESC";
        List<Map> results = neo4jClient.query(cypherQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Object> centerMap = new HashMap<>();
                    centerMap.put("name", record.get("name"));
                    centerMap.put("score", record.get("score"));
                    return centerMap;
                }).all().stream().toList();
        System.out.println(results);
    }

}
