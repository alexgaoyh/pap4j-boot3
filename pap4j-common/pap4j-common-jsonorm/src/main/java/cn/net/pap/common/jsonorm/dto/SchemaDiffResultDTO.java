package cn.net.pap.common.jsonorm.dto;

import java.util.Map;
import java.util.Set;

/**
 * <p>JSON Schema 版本差异分析结果。</p>
 * <p>提供了两个 Schema 版本之间在“核心提取标记（x-extract）”上的增量变化，是数据库 DDL 自动生成的依据。</p>
 *
 * @param addedFields   新增的提取字段映射。
 *                      <p>Key 为字段路径，Value 为详细的类型元数据 {@link FieldInfoDTO}。</p>
 * @param removedFields 被移除提取标记或从 Schema 中删除的字段路径集合。
 * @author
 * @since 2026-05-26
 */
public record SchemaDiffResultDTO(Map<String, FieldInfoDTO> addedFields, Set<String> removedFields) {
}
