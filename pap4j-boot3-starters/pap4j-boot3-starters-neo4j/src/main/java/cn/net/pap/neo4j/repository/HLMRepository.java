package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.HLMEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface HLMRepository extends Neo4jRepository<HLMEntity, String> {

}
