package cn.net.pap.example.dynamic.form.dto;

import java.io.Serializable;

/**
 * <p>基于 JSON Schema 随机生成业务数据的请求参数。</p>
 *
 * @param schemaJson 标准 JSON Schema（Draft-07）字符串
 * @param count      生成条数（为空默认 10，上限 200）
 * @param seed       随机种子（可选，传同一 seed 可复现同一批数据）
 */
public record MockGenerateRequest(String schemaJson, Integer count, Long seed) implements Serializable {
}
