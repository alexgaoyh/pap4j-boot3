package cn.net.pap.example.proguard.listener;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 JPA 标准规范的实体变更日志监听器。
 * <p>
 * 本类通过 {@link PostPersist}, {@link PostUpdate}, {@link PostRemove} 拦截实体的变更动作，
 * 当前主要用于在开发和调试阶段（Debug级别）输出实体的变更日志。
 * </p>
 *
 * <h3>✨ 核心亮点 (Highlights)</h3>
 * <ul>
 *     <li><b>极度轻量且标准</b>：基于 JPA 原生规范，零第三方依赖，开箱即用。</li>
 *     <li><b>极致的性能保护</b>：所有日志输出均被 {@code log.isDebugEnabled()} 守卫，生产环境下性能损耗近乎为零。</li>
 * </ul>
 *
 * <h3>⚠️ 局限性与设计陷阱 (Shortcomings)</h3>
 * <ul>
 *     <li><b>触发时机警告</b>：JPA 的 {@code @Post*} 注解是在 EntityManager 执行 {@code flush()} 后触发的，<b>此时数据库事务并未真正提交 (Commit)！</b></li>
 *     <li><b>无法比对旧值</b>：仅包含实体的最新状态，无法得知修改前的旧值，无法用于精细化的字段级审计。</li>
 * </ul>
 *
 * <h3>🚫 绝对禁忌 (The Absolute Taboos)</h3>
 * <p>
 * 鉴于本监听器是在<b>事务提交前</b>同步执行，此处<b>绝对禁止</b>以下行为：
 * </p>
 * <ul>
 *     <li><b>❌ 绝对禁止执行“不可撤销”的外部调用</b>：如发送 MQ、调用外部 API 等，防止事务回滚产生脏动作。</li>
 *     <li><b>❌ 绝对禁止抛出未捕获的异常</b>：未被 catch 的异常将强制回滚核心业务事务。</li>
 * </ul>
 *
 * @see jakarta.persistence.EntityListeners
 */
public class EntityDebugLoggerListener {

    private static final Logger log = LoggerFactory.getLogger(EntityDebugLoggerListener.class);

    @PostPersist
    public void onPostPersist(Object entity) {
        if(log.isDebugEnabled()) {
            log.debug("触发实体保存动作 (PostPersist): {}", entity);
        }
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        if(log.isDebugEnabled()) {
            log.debug("触发实体更新动作 (PostUpdate): {}", entity);
        }
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        if(log.isDebugEnabled()) {
            log.debug("触发实体删除动作 (PostRemove): {}", entity);
        }
    }

}