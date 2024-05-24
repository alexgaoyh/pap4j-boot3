package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.DepartmentEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface DepartmentRepository extends Neo4jRepository<DepartmentEntity, String> {

}
