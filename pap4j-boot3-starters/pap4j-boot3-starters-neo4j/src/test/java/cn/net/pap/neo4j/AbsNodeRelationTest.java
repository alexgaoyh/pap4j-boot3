package cn.net.pap.neo4j;

import cn.net.pap.neo4j.dto.AbsNodeWithChildrensDTO;
import cn.net.pap.neo4j.dto.AbsNodeWithTypeDTO;
import cn.net.pap.neo4j.entity.AbsNodeEntity;
import cn.net.pap.neo4j.repository.AbsNodeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.internal.value.PathValue;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SpringBootTest(classes = {Neo4jApplication.class})
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
public class AbsNodeRelationTest extends Neo4jBaseTest {

    private final AbsNodeRepository absNodeRepository;

    public AbsNodeRelationTest(AbsNodeRepository absNodeRepository) {
        this.absNodeRepository = absNodeRepository;
    }

    @Test
    public void insert() {
        AbsNodeEntity child1 = new AbsNodeEntity("child1", "child1", "child");
        AbsNodeEntity child2 = new AbsNodeEntity("child2", "child2", "child");
        AbsNodeEntity child3 = new AbsNodeEntity("child3", "child3", "child");
        Set<AbsNodeEntity> childSet = new HashSet<>(3) {{
            add(child1);
            add(child2);
            add(child3);
        }};

        AbsNodeEntity parent1 = new AbsNodeEntity("parent1", "parent1", "parent");
        AbsNodeEntity parent2 = new AbsNodeEntity("parent2", "parent2", "parent");
        Set<AbsNodeEntity> parentSet = new HashSet<>(3) {{
            add(parent1);
            add(parent2);
        }};

        child1.setParents(parentSet);
        child2.setParents(parentSet);
        child3.setParents(parentSet);
        absNodeRepository.save(child1);
        absNodeRepository.save(child2);
        absNodeRepository.save(child3);
        parent1.setChildrens(childSet);
        parent2.setChildrens(childSet);
        absNodeRepository.save(parent1);
        absNodeRepository.save(parent2);
    }

    @Test
    public void findByAbsNodeLabelTest() {
        List<AbsNodeEntity> child1 = absNodeRepository.findByAbsNodeLabel("child1");
        System.out.println(child1);

        List<AbsNodeEntity> child1RelationList = absNodeRepository.getRelationByAbsNodeLabel("child1");
        System.out.println(child1RelationList);

        try {
            List<AbsNodeWithTypeDTO> child1AbsNodeWithTypeDTOList = absNodeRepository.getAbsNodeWithTypeDTOsByAbsNodeLabel("child1");
            ObjectMapper objectMapperJsonIdentityInfo = new ObjectMapper();
            String absNodeWithTypeDTOStr = objectMapperJsonIdentityInfo.writeValueAsString(child1AbsNodeWithTypeDTOList);
            System.out.println(absNodeWithTypeDTOStr);
        } catch (JsonProcessingException e) {
        }

        List<List<PathValue>> pathBetweenNodesByAbsNodeLabelList = absNodeRepository.getPathBetweenNodesByAbsNodeLabel("parent1", "parent2");
        System.out.println(pathBetweenNodesByAbsNodeLabelList);

        List<List<PathValue>> shortestPathBetweenNodesByAbsNodeLabelList = absNodeRepository.getShortestPathBetweenNodesByAbsNodeLabel("parent1", "parent2");
        System.out.println(shortestPathBetweenNodesByAbsNodeLabelList);

        List<AbsNodeWithChildrensDTO> parentWithChildrensList = AbsNodeWithChildrensDTO.convert(absNodeRepository.getParentWithChildrens("parent1"));
        System.out.println(parentWithChildrensList);
    }

}
