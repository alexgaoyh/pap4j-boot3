package cn.net.pap.example.dynamic.form.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * <p>随机数据生成响应。</p>
 *
 * @param records 生成的业务数据列表，每条均符合 schema 约束
 */
public record MockGenerateResponse(List<Map<String, Object>> records) implements Serializable {
}
