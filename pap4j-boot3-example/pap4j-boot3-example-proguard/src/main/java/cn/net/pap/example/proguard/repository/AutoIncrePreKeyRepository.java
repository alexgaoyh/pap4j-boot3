package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface AutoIncrePreKeyRepository extends JpaRepository<AutoIncrePreKey, Long>, JpaSpecificationExecutor<AutoIncrePreKey> {

    /**
     * 使用临时表 JOIN，替代 IN 查询
     */
    @Query("""
        SELECT a
        FROM AutoIncrePreKey a
        JOIN TempQuery t ON a.id = t.id
        WHERE t.bizType = :bizType
    """)
    List<AutoIncrePreKey> findByTempQueryBizType(@Param("bizType") String bizType);


    @Query("""
        select a
        from AutoIncrePreKey a
        where a.id in :ids
    """)
    List<AutoIncrePreKey> findByIdIn(@Param("ids") List<Long> ids);

}
