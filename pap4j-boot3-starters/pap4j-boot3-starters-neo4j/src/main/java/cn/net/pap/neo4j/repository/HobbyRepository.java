package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.HobbyEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface HobbyRepository extends Neo4jRepository<HobbyEntity, String> {

}
