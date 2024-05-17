package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.PersonEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface PersonRepository extends Neo4jRepository<PersonEntity, String> {

    public List<PersonEntity> findByPersonName(String personName);

}
