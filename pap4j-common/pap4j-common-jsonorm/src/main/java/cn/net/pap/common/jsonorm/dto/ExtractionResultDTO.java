package cn.net.pap.common.jsonorm.dto;

import java.util.Map;

/**
 * <p>数据提取结果传输对象。</p>
 * <p>封装了基于 JSON Schema 投影后的核心字段集合以及原始的完整数据负载。</p>
 *
 * @param coreFields 提取出的核心字段映射。
 *                   <ul>
 *                     <li><b>Key:</b> 字段的扁平化路径（如 "user.address.city" 或 "items[0].price"）</li>
 *                     <li><b>Value:</b> 提取出的原始 Java 对象（String, BigDecimal, List 等）</li>
 *                   </ul>
 * @param payload    原始的完整 JSON 字符串，用于在“二八原则”下存储长尾数据。
 * @author
 * @since 2026-05-26
 */
public record ExtractionResultDTO(Map<String, Object> coreFields, String payload) {
}
