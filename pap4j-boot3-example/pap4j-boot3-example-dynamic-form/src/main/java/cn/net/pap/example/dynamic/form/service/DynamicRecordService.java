package cn.net.pap.example.dynamic.form.service;

import cn.net.pap.example.dynamic.form.entity.DynamicFieldValue;
import cn.net.pap.example.dynamic.form.entity.DynamicRecord;
import cn.net.pap.example.dynamic.form.entity.DynamicRelation;
import cn.net.pap.example.dynamic.form.repository.DynamicRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>动态记录核心业务服务 (Core Dynamic Record Service)</p>
 * <p>实现 EAV 模型的核心逻辑，包括<b>递归持久化</b>和<b>结构化重建</b>。</p>
 *
 * <h3>核心特性:</h3>
 * <ol>
 *   <li><b>递归保存:</b> 支持深度嵌套的 JSON Payload 自动解析为 Record/Value/Relation。</li>
 *   <li><b>结构重建:</b> 从关系型数据库还原原始的嵌套 Map 结构。</li>
 *   <li><b>自动路由:</b> 根据数据类型自动选择存储列。</li>
 * </ol>
 *
 */
@Service
@RequiredArgsConstructor
public class DynamicRecordService {

    private final DynamicRecordRepository recordRepository;

    /**
     * 保存复杂嵌套记录
     *
     * @param formCode 表单编码
     * @param payload  JSON 数据载体
     * @return 生成的根记录 ID
     */
    @Transactional
    public Long saveComplexRecord(String formCode, Map<String, Object> payload) {
        DynamicRecord rootRecord = createRecordRecursive(formCode, payload);
        return recordRepository.save(rootRecord).getId();
    }

    /**
     * 递归创建记录及关联关系
     */
    private DynamicRecord createRecordRecursive(String formCode, Map<String, Object> payload) {
        DynamicRecord record = new DynamicRecord();
        record.setFormCode(formCode);

        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                DynamicRecord childRecord = createRecordRecursive(key, (Map<String, Object>) value);
                DynamicRelation relation = new DynamicRelation();
                relation.setRelationCode(key);
                relation.setRelationType("ONE_TO_ONE");
                relation.setTargetRecord(childRecord);
                record.addRelation(relation);
            } else if (value instanceof List) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) value;
                for (Map<String, Object> item : items) {
                    DynamicRecord childRecord = createRecordRecursive(key, item);
                    DynamicRelation relation = new DynamicRelation();
                    relation.setRelationCode(key);
                    relation.setRelationType("ONE_TO_MANY");
                    relation.setTargetRecord(childRecord);
                    record.addRelation(relation);
                }
            } else {
                DynamicFieldValue fieldValue = new DynamicFieldValue();
                fieldValue.setFieldKey(key);
                setFieldValue(fieldValue, value);
                record.addFieldValue(fieldValue);
            }
        }
        return record;
    }

    /**
     * 根据数据类型设置字段值
     */
    private void setFieldValue(DynamicFieldValue fieldValue, Object value) {
        if (value == null) return;
        if (value instanceof String s) {
            if (s.length() > 255) {
                fieldValue.setTextValue(s);
            } else {
                fieldValue.setStringValue(s);
            }
        } else if (value instanceof Number n) {
            fieldValue.setNumValue(n.doubleValue());
        } else if (value instanceof LocalDateTime ldt) {
            fieldValue.setDateValue(ldt);
        } else {
            fieldValue.setStringValue(value.toString());
        }
    }

    /**
     * 获取指定类型的记录列表
     *
     * @param formCode 表单编码
     * @return 还原后的 Map 列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecords(String formCode) {
        return recordRepository.findByFormCode(formCode).stream()
                .map(this::reconstructRecord)
                .collect(Collectors.toList());
    }

    /**
     * 获取单条完整记录
     *
     * @param id 记录 ID
     * @return 还原后的结构化 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRecord(Long id) {
        return recordRepository.findById(id)
                .map(this::reconstructRecord)
                .orElseThrow(() -> new RuntimeException("Record not found: " + id));
    }

    /**
     * 删除记录（支持级联删除值和关系）
     *
     * @param id 记录 ID
     */
    @Transactional
    public void deleteRecord(Long id) {
        recordRepository.deleteById(id);
    }

    /**
     * 将关系型实体还原为结构化 Map
     */
    private Map<String, Object> reconstructRecord(DynamicRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_id", record.getId());
        map.put("_formCode", record.getFormCode());

        for (DynamicFieldValue fv : record.getFieldValues()) {
            Object value = null;
            if (fv.getStringValue() != null) value = fv.getStringValue();
            else if (fv.getTextValue() != null) value = fv.getTextValue();
            else if (fv.getNumValue() != null) value = fv.getNumValue();
            else if (fv.getDateValue() != null) value = fv.getDateValue();
            map.put(fv.getFieldKey(), value);
        }

        Map<String, List<Map<String, Object>>> relationsMap = new HashMap<>();
        for (DynamicRelation rel : record.getRelations()) {
            String code = rel.getRelationCode();
            Map<String, Object> childMap = reconstructRecord(rel.getTargetRecord());

            if ("ONE_TO_MANY".equals(rel.getRelationType())) {
                relationsMap.computeIfAbsent(code, k -> new ArrayList<>()).add(childMap);
            } else {
                map.put(code, childMap);
            }
        }

        map.putAll(relationsMap);
        return map;
    }
}
