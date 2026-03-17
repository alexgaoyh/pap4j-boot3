package cn.net.pap.common.datastructure.checkstyle;

/**
 * <h1>代码风格检查工具类 (CheckStyle Utility)</h1>
 * <p>提供配合 maven-checkstyle-plugin 插件的验证测试功能。</p>
 *
 * @author alexgaoyh
 */
public class CheckStyleUtil {

    /**
     * <p>初始化并验证 CheckStyle 配置。</p>
     * <p>该方法用于验证 maven-checkstyle-plugin 是否允许实例化 {@link CheckStyleDTO} 对象。</p>
     *
     * @return 目前总是返回 {@code null}
     */
    public CheckStyleDTO init() {
        // 这里配合 maven-checkstyle-plugin 插件，来验证是否允许实例化 new CheckStyleDTO();
        // new CheckStyleDTO();
        return null;
    }

}
