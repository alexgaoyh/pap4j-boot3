package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.callback.TempQueryCallback;
import cn.net.pap.example.proguard.entity.NumberSegment;
import cn.net.pap.example.proguard.entity.TempQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ITempQueryService {

    /**
     * 批量写入临时查询ID（幂等）
     * @param bizType
     * @param ids
     */
    void batchInsert(String bizType, Collection<Long> ids);

    /**
     * 根据业务类型查询
     * @param bizType
     * @return 结果集合
     */
    List<TempQuery> listByBizType(String bizType);

    /**
     * 根据业务类型 + ID 集合查询
     * @param bizType
     * @param ids
     * @return 结果集合
     */
    List<TempQuery> listByBizTypeAndIds(String bizType, Collection<Long> ids);

    /**
     * 根据业务类型统计数量
     * @param bizType
     * @return 处理结果值
     */
    long countByBizType(String bizType);

    /**
     * 判断是否存在
     * @param bizType
     * @param id
     * @return 是否处理成功
     */
    boolean exists(String bizType, Long id);

    /**
     * 清理某个业务类型下的临时数据
     * @param bizType
     * @return 处理结果值
     */
    int deleteByBizType(String bizType);

    /**
     * ID 范围查询（跨业务）
     * @param startId
     * @param endId
     * @return 结果集合
     */
    List<TempQuery> listByIdRange(Long startId, Long endId);

    /**
     * save
     * @param tempQuery
     */
    void save(TempQuery tempQuery);

    /**
     * 在一个临时业务上下文中执行一段查询逻辑
     * @param bizType
     * @param ids
     * @param callback
     * @return 处理结果对象
     * @param <T>
     */
    <T> T executeWithTempQuery(String bizType, Collection<Long> ids, TempQueryCallback<T> callback);

}
