package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.PersonEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface PersonRepository extends Neo4jRepository<PersonEntity, String> {

    /**
     * 根据 personName 查询
     * @param personName
     * @return
     */
    public List<PersonEntity> findByPersonName(String personName);

}
