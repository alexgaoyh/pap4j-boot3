package cn.net.pap.common.qlexpress.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * <p>函数式提取结果传输对象。</p>
 */
public record FunctionalExtractionResultDTO(Map<String, Object> extractedFields,
                                            String rawPayload) implements Serializable {
}
