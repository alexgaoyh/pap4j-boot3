package cn.net.pap.common.datastructure.checkstyle;

import java.io.Serializable;

/**
 * <h1>代码风格检查配置传输对象 (CheckStyle DTO)</h1>
 * <p>表示 CheckStyle 检查的数据载体，用于相关规则校验传递等操作。</p>
 *
 * @author alexgaoyh
 */
public class CheckStyleDTO implements Serializable {

    /**
     * <p>规则或配置的主键 ID。</p>
     */
    private String id;

    /**
     * <p>获取 ID。</p>
     *
     * @return 当前对象的 ID
     */
    public String getId() {
        return id;
    }

    /**
     * <p>设置 ID。</p>
     *
     * @param id 要设置的 ID 字符串
     */
    public void setId(String id) {
        this.id = id;
    }
}
