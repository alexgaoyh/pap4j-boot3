package cn.net.pap.common.jsonorm.dto;

/**
 * <p>字段元数据详情对象。</p>
 * <p>用于描述 Schema 中被标记为 <code>x-extract</code> 的字段属性，支持物理存储层的自动演进。</p>
 *
 * @param path     字段的完整访问路径。支持：
 *                 <ul>
 *                   <li>对象嵌套：<code>parent.child</code></li>
 *                   <li>数组索引：<code>location[0]</code></li>
 *                   <li>正则匹配：<code>attributes.&lt;pattern:^ext_.*&gt;</code></li>
 *                 </ul>
 * @param jsonType JSON Schema 中的原始类型，如 string, number, object 等。
 * @param javaType 映射到 Java 运行时的类型名称，如 BigDecimal, Long, String 等。
 * @author
 * @since 2026-05-26
 */
public record FieldInfoDTO(String path, String jsonType, String javaType) {
}
