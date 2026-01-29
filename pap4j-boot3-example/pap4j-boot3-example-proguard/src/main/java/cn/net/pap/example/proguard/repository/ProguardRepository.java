package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.Proguard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProguardRepository extends JpaRepository<Proguard,Long>, JpaSpecificationExecutor<Proguard> {

    List<Proguard> searchAllByProguardName(@Param("proguardName") String proguardName);

    Proguard getProguardByProguardId(@Param("proguardId") Long proguardId);

    @Query("SELECT o FROM Proguard o WHERE o.proguardId IN :proguardIds")
    List<Proguard> getProguardByProguardIds(@Param("proguardIds") List<Long> proguardIds);

    /**
     * 查询指定字段
     * @param proguardId
     * @param type 形如 ProguardDTO.java 这个 interface
     * @return
     */
    <T> Optional<T> getProguardByProguardId(@Param("proguardId") Long proguardId, Class<T> type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Proguard a WHERE a.proguardId = :proguardId")
    Proguard getProguardByProguardIdForUpdate(@Param("proguardId") Long proguardId);

    /**
     * 避免深度分页，增加延迟关联分页
     * @param proguardName
     * @param pageable
     * @return
     */
    @Query(
            value = """
                SELECT t.*
                FROM proguard t
                INNER JOIN (
                    SELECT proguard_id
                    FROM proguard
                    WHERE 1 = 1
                      AND (:proguardName IS NULL OR proguard_name = :proguardName)
                    ORDER BY proguard_id desc
                    LIMIT :#{#pageable.offset}, :#{#pageable.pageSize}
                ) tmp ON t.proguard_id = tmp.proguard_id
                ORDER BY t.proguard_id desc
            """,
            countQuery = """
                SELECT COUNT(1)
                FROM proguard
                WHERE 1 = 1
                  AND (:proguardName IS NULL OR proguard_name = :proguardName)
            """,
            nativeQuery = true
    )
    Page<Proguard> pageByProguardNameDeepPaging(
            @Param("proguardName") String proguardName,
            Pageable pageable
    );

}
