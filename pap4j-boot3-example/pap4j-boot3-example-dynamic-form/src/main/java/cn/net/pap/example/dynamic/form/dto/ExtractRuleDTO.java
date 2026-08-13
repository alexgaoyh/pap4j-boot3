package cn.net.pap.example.dynamic.form.dto;

import java.io.Serializable;

/**
 * <p>前端提交的数据提取规则。</p>
 * <p>表达式支持两种路由：以 {@code $} 开头走 JsonPath，其余走 QLExpress（脚本内注入
 * {@code json} 解析后对象 与 {@code data} 原始字符串 两个上下文变量）。</p>
 *
 * @param targetField 提取结果的目标字段名
 * @param expression  提取表达式（JsonPath 或 QLExpress）
 */
public record ExtractRuleDTO(String targetField, String expression) implements Serializable {
}
