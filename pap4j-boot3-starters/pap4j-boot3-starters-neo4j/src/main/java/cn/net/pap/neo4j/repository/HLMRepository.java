package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.HLMEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface HLMRepository extends Neo4jRepository<HLMEntity, String> {

    public List<HLMEntity> findByName(String name);

    /**
     * 根据ID 匹配数据，这里的 ID 是 neo4j里默认会创建的一个Long型自增ID
     * @param id
     * @return
     */
    @Query("MATCH (n:HLM) WHERE id(n)={id} RETURN n")
    public HLMEntity getById(Long id);

    /**
     * 查询根节点，顶级 无入
     * @return
     */
    @Query("MATCH (n) WHERE not ()-->(n) RETURN DISTINCT n")
    public List<HLMEntity> getRoot();

    /**
     * 查询叶子节点，底级 无出
     * @return
     */
    @Query("MATCH (n) WHERE not (n)-->() RETURN DISTINCT n")
    public List<HLMEntity> getLeaf();

    /**
     * 查询循环节点
     * @return
     */
    @Query("MATCH (n)-[*]->(n) RETURN DISTINCT n")
    public List<HLMEntity> getCycle();
}
