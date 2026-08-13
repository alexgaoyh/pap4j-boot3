package cn.net.pap.example.dynamic.form.dto;

import java.io.Serializable;
import java.util.List;

/**
 * <p>数据提取响应。</p>
 *
 * @param results 逐记录提取结果，与请求 records 顺序一致
 */
public record ExtractResponse(List<ExtractRecordResult> results) implements Serializable {
}
