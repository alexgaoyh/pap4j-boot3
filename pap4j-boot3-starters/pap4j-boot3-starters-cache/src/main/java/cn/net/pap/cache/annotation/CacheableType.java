package cn.net.pap.cache.annotation;

/**
 * 缓存类型
 */
public @interface CacheableType {

    /**
     * 字段名称
     * @return
     */
    String field();

    /**
     * 字段类型
     * @return
     */
    String type();

}
