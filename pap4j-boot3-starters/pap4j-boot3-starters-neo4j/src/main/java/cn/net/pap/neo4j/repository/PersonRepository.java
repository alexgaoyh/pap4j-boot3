package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.PersonEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface PersonRepository extends Neo4jRepository<PersonEntity, String> {

}
