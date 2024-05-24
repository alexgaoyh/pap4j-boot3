package cn.net.pap.neo4j.repository;

import cn.net.pap.neo4j.entity.DepartmentEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DepartmentRepository extends Neo4jRepository<DepartmentEntity, String> {

    /**
     * 查找兄弟，有同样的根
     * @param remark
     * @return
     */
    @Query("MATCH (dept:department)-[:parent]->(top:department)<-[:parent]-(d:department {remark: {remark}}) WHERE dept <> d RETURN dept")
    List<DepartmentEntity> getBrothersByRemark(@Param("remark") String remark);

}
