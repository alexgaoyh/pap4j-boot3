package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.HLMEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface HLMRepository extends Neo4jRepository<HLMEntity, String> {

    public List<HLMEntity> findByName(String name);
}
