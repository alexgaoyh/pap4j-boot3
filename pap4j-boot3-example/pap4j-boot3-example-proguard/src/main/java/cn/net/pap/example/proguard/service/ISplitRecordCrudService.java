package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.dto.SplitRecordDTO;

/**
 * 分表记录读写接口（业务表的唯一访问入口）。
 *
 * <p>所有业务表字段同构，表名即业务、操作时动态传入；用固定传输类型 {@link SplitRecordDTO} 而非
 * {@code @Entity}（无 Hibernate 托管状态，规避跨表同 id 串表与 auto-flush 污染）。</p>
 *
 * <p>v1 范围：建表 + 保存 + 单条查询；更新 / 删除 / 分页 / 高级查询（queryByField*）后续补齐。</p>
 *
 * <p>本接口只关心 DB：数据值的业务校验由上游 JSON Schema 负责；表名白名单（防 SQL 注入）在实现类内联。</p>
 */
public interface ISplitRecordCrudService {

    /**
     * 建表（幂等）：{@code CREATE TABLE IF NOT EXISTS}。
     * DDL 自动提交、不随业务事务回滚；MySQL 下隐式提交，勿在 {@code @Transactional} 方法内调用。
     * 生产环境尽量不用自增，以保证不同数据库下规则一致（自增语法随方言而异）。
     */
    void createTableIfNotExists(String tableName);

    /**
     * 新增：data 与 ext 检索副本同一事务双写，返回 DB 自增 id 并回填到 {@code dto.id}。
     */
    Long save(String tableName, SplitRecordDTO dto);

    /**
     * 单条读取：按 id 查询，无结果返回 null。
     */
    SplitRecordDTO get(String tableName, Long id);
}
