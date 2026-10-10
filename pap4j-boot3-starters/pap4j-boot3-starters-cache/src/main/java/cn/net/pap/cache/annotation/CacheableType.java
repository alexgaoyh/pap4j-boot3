package cn.net.pap.cache.annotation;

/**
 * 缓存类型
 */
public @interface CacheableType {

    /**
     * 字段名称
     * @return 文本结果
     */
    String field();

    /**
     * 字段类型
     * @return 文本结果
     */
    String type();

}
