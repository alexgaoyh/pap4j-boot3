package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.AbsNodeEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface AbsNodeRepository extends Neo4jRepository<AbsNodeEntity, String> {

    public List<AbsNodeEntity> findByAbsNodeLabel(String absNodeLabel);

}
