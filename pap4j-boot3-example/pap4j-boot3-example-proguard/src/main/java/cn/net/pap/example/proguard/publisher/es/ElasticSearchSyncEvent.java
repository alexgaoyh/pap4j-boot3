package cn.net.pap.example.proguard.publisher.es;

import java.util.List;

public class ElasticSearchSyncEvent<T> {

    private final String index;

    private final SyncType type;

    private final List<T> data;

    public ElasticSearchSyncEvent(String index, SyncType type, List<T> data) {
        this.index = index;
        this.type = type;
        this.data = data;
    }

    public String getIndex() {
        return index;
    }

    public SyncType getType() {
        return type;
    }

    public List<T> getData() {
        return data;
    }

    /**
     * Elasticsearch 数据同步操作类型枚举。
     * <p>
     * 本枚举类的设计遵循“大道至简”的原则，将复杂的业务动作（如：上架、下架、推荐、修改标题等）
     * 统一抽象为底层搜索引擎最核心的两种写操作，极大地降低了业务方触发事件的复杂度和认知负担。
     * </p>
     * * <h3>架构视角：</h3>
     * <ul>
     * <li><strong>屏蔽业务语义</strong>：ES 作为一个文档型搜索引擎，无需感知上层的业务流转。</li>
     * <li><strong>拥抱幂等性</strong>：摒弃复杂的局部更新（Partial Update），采用“查 DB 全量最新数据并覆盖”的策略，天然具备极强的容错性，无惧并发乱序或重试。</li>
     * </ul>
     *
     */
    public enum SyncType {

        /**
         * 写入或全量覆盖操作 (UPSERT 语义)。
         * <p>
         * 对应 ES 的 Index API。不论是新增数据、修改任意字段，还是执行逻辑删除（仅修改 {@code is_deleted} 等状态位），
         * 均使用此类型。
         * </p>
         * <p>
         * <b>处理规约：</b>消费端在处理此事件时，必须根据传入的 ID 重新查询数据库，获取最新的全量实体进行覆盖写入。
         * </p>
         */
        UPDATE,

        /**
         * 物理删除操作 (DELETE 语义)。
         * <p>
         * 对应 ES 的 Delete API。仅当数据库执行了真正的 {@code DELETE FROM ...} 语句触发物理删除时，才使用此类型。
         * </p>
         * <p>
         * <b>处理规约：</b>消费端在处理此事件时，<b>严禁回查数据库</b>（由于事务已提交，必然查不到数据），
         * 必须直接根据传入的 ID 列表构建删除请求，清理 ES 文档。
         * </p>
         */
        DELETE,
    }

}
