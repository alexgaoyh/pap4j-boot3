package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.TempQuery;
import cn.net.pap.example.proguard.entity.TempQueryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TempQueryRepository extends JpaRepository<TempQuery, TempQueryId> {

    /**
     * 根据业务类型查询
     *
     * @param bizType
     * @return
     */
    List<TempQuery> findByBizType(String bizType);

    /**
     * 根据业务类型和ID列表查询
     *
     * @param bizType
     * @param ids
     * @return
     */
    List<TempQuery> findByBizTypeAndIdIn(String bizType, Collection<Long> ids);

    /**
     * 根据业务类型统计数量
     *
     * @param bizType
     * @return
     */
    long countByBizType(String bizType);

    /**
     * 检查是否存在
     *
     * @param bizType
     * @param id
     * @return
     */
    boolean existsByBizTypeAndId(String bizType, Long id);

    /**
     * 根据业务类型删除
     *
     * @param bizType
     * @return
     */
    @Modifying
    @Query("DELETE FROM TempQuery t WHERE t.bizType = :bizType")
    int deleteByBizType(@Param("bizType") String bizType);

    /**
     * 根据ID范围查询（即使跨业务类型）
     *
     * @param startId
     * @param endId
     * @return
     */
    @Query("SELECT t FROM TempQuery t WHERE t.id BETWEEN :startId AND :endId")
    List<TempQuery> findByIdRange(@Param("startId") Long startId, @Param("endId") Long endId);

    /**
     * 根据业务类型分页查询
     *
     * @param bizType
     * @return
     */
    @Query("SELECT t FROM TempQuery t WHERE t.bizType = :bizType ORDER BY t.id")
    List<TempQuery> findByBizTypeOrderById(@Param("bizType") String bizType);

}
