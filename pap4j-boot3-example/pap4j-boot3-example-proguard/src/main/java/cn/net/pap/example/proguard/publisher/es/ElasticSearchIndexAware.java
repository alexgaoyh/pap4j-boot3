package cn.net.pap.example.proguard.publisher.es;

/**
 * 实体接口
 */
public interface ElasticSearchIndexAware {

    /**
     * 返回 ES index 名称
     * @return 文本结果
     */
    String esIndex();

}
