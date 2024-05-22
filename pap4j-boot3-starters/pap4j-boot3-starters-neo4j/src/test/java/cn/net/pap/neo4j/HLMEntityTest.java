package cn.net.pap.neo4j;

import cn.net.pap.neo4j.dto.HLMNodeWithTypeDTO;
import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.repository.HLMRelationshipRepository;
import cn.net.pap.neo4j.repository.HLMRepository;
import cn.net.pap.neo4j.util.kg.HLMEntity2KGConvert;
import cn.net.pap.neo4j.util.kg.PathValue2KGConvert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.driver.internal.value.PathValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Neo4jApplication.class})
public class HLMEntityTest {

    @Autowired
    private HLMRepository hlmRepository;

    @Autowired
    private HLMRelationshipRepository hlmRelationshipRepository;

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
}
