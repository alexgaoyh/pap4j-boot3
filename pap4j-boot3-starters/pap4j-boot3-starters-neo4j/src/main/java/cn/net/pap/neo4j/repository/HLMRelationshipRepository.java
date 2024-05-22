package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.dto.HLMNodeWithTypeDTO;
import cn.net.pap.neo4j.entity.HLMRelationshipEntity;
import org.neo4j.driver.internal.value.PathValue;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HLMRelationshipRepository extends Neo4jRepository<HLMRelationshipEntity, Long> {


    /**
     * 根据 startNode.name 查询关联节点，并制定节点方向
     *
     * @param startNodeName
     * @return
     */
    @Query("MATCH (HLM { name: {startNodeName} })-[r]->(node) RETURN DISTINCT node AS node, r.type AS nodeType, 'OUT' AS direction " +
            "UNION  " +
            "MATCH (HLM { name: {startNodeName} })<-[r]-(node) RETURN DISTINCT node AS node, r.type AS nodeType, 'IN' AS direction ")
    public List<HLMNodeWithTypeDTO> findByStartNodeName(String startNodeName);

    /**
     * 节点路径 最短距离
     *
     * @param startNodeName
     * @param endNodeName
     * @return
     */
    @Query("match p = shortestpath((a:HLM)-[r*0..4]-(b:HLM)) where a.name = {startNodeName} and b.name={endNodeName} return p")
    List<List<PathValue>> getShortestPathBetweenNodesByName(@Param("startNodeName") String startNodeName, @Param("endNodeName") String endNodeName);

}
