package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.dto.AbsNodeWithTypeDTO;
import cn.net.pap.neo4j.entity.AbsNodeEntity;
import org.neo4j.driver.internal.value.PathValue;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AbsNodeRepository extends Neo4jRepository<AbsNodeEntity, String> {

    public List<AbsNodeEntity> findByAbsNodeLabel(String absNodeLabel);

    /**
     * 与 absNodeLabel 有关联的节点.
     * @param absNodeLabel
     * @return
     */
    @Query("MATCH (absNodeEntity { absNodeLabel: {absNodeLabel} })--(node) RETURN DISTINCT node")
    public List<AbsNodeEntity> getRelationByAbsNodeLabel(String absNodeLabel);

    /**
     * 查询与当前节点关联的节点和关联关系
     * @param absNodeLabel
     * @return
     */
    @Query("MATCH (absNodeEntity { absNodeLabel: {absNodeLabel} })-[r]-(node) RETURN DISTINCT node AS node, TYPE(r) AS nodeType")
    List<AbsNodeWithTypeDTO> getAbsNodeWithTypeDTOsByAbsNodeLabel(@Param("absNodeLabel") String absNodeLabel);

    /**
     * 节点路径距离查询
     * @param startAbsNodeLabel
     * @param endAbsNodeLabel
     * @return
     */
    @Query("match p = (a:absNodeEntity)-[r*..5]-(b:absNodeEntity) where a.absNodeLabel = {startAbsNodeLabel} and b.absNodeLabel={endAbsNodeLabel} and ALL( n1 in nodes(p) where size([n2 in nodes(p) where id(n1) = id(n2)])=1 ) return p")
    List<List<PathValue>> getPathBetweenNodesByAbsNodeLabel(@Param("startAbsNodeLabel") String startAbsNodeLabel, @Param("endAbsNodeLabel") String endAbsNodeLabel);

    /**
     * 节点路径 最短距离
     * @param startAbsNodeLabel
     * @param endAbsNodeLabel
     * @return
     */
    @Query("match p = shortestpath((a:absNodeEntity)-[r*0..4]-(b:absNodeEntity)) where a.absNodeLabel = 'parent1' and b.absNodeLabel='parent2' return p")
    List<List<PathValue>> getShortestPathBetweenNodesByAbsNodeLabel(@Param("startAbsNodeLabel") String startAbsNodeLabel, @Param("endAbsNodeLabel") String endAbsNodeLabel);

}
