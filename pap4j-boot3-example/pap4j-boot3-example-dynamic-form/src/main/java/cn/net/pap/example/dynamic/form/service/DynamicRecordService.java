package cn.net.pap.example.dynamic.form.service;

import cn.net.pap.example.dynamic.form.entity.DynamicFieldValue;
import cn.net.pap.example.dynamic.form.entity.DynamicRecord;
import cn.net.pap.example.dynamic.form.entity.DynamicRelation;
import cn.net.pap.example.dynamic.form.repository.DynamicFieldValueRepository;
import cn.net.pap.example.dynamic.form.repository.DynamicRecordRepository;
import cn.net.pap.example.dynamic.form.repository.DynamicRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

    private final DynamicFieldValueRepository fieldValueRepository;

    private final DynamicRelationRepository relationRepository;

    /**
     * 保存复杂嵌套记录
     *
     * @param formCode 表单编码
     * @param payload  JSON 数据载体
     * @return 生成的根记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveComplexRecord(String formCode, Map<String, Object> payload) {
        DynamicRecord rootRecord = createRecordRecursive(formCode, payload);
        saveRecordRecursive(rootRecord);
        return rootRecord.getId();
    }

    private void saveRecordRecursive(DynamicRecord record) {
        recordRepository.save(record);
        Long recordId = record.getId();

        for (DynamicFieldValue fv : record.getFieldValues()) {
            fv.setRecordId(recordId);
            fieldValueRepository.save(fv);
        }

        for (DynamicRelation rel : record.getRelations()) {
            rel.setSourceRecordId(recordId);
            DynamicRecord targetRec = rel.getTargetRecord();
            if (targetRec != null) {
                saveRecordRecursive(targetRec);
                rel.setTargetRecordId(targetRec.getId());
            }
            relationRepository.save(rel);
        }
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
    public Page<Map<String, Object>> listRecords(String formCode, Pageable pageable) {
        Page<DynamicRecord> records = recordRepository.findByFormCode(formCode, pageable);
        populateRecordsBatch(records.getContent());
        List<Map<String, Object>> list = records.getContent().stream()
                .map(this::reconstructRecord)
                .collect(Collectors.toList());
        return new PageImpl<>(list, pageable, records.getTotalElements());
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
                .map(record -> {
                    populateRecord(record);
                    return reconstructRecord(record);
                })
                .orElseThrow(() -> new RuntimeException("Record not found: " + id));
    }

    /**
     * 删除记录（支持级联删除值和关系）
     *
     * @param id 记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long id) {
        // 1. 递归删除嵌套的关系与记录
        List<DynamicRelation> rels = relationRepository.findBySourceRecordId(id);
        for (DynamicRelation rel : rels) {
            if (rel.getTargetRecordId() != null) {
                deleteRecord(rel.getTargetRecordId());
            }
            relationRepository.delete(rel);
        }

        // 2. 删除当前记录关联的字段值
        fieldValueRepository.deleteByRecordId(id);

        // 3. 删除记录本体
        recordRepository.deleteById(id);
    }

    private void populateRecord(DynamicRecord record) {
        if (record == null) return;
        List<DynamicFieldValue> fvs = fieldValueRepository.findByRecordId(record.getId());
        record.setFieldValues(fvs);

        List<DynamicRelation> rels = relationRepository.findBySourceRecordId(record.getId());
        for (DynamicRelation rel : rels) {
            if (rel.getTargetRecordId() != null) {
                DynamicRecord targetRecord = recordRepository.findById(rel.getTargetRecordId()).orElse(null);
                if (targetRecord != null) {
                    populateRecord(targetRecord);
                    rel.setTargetRecord(targetRecord);
                }
            }
        }
        record.setRelations(rels);
    }

    private void populateRecordsBatch(List<DynamicRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        List<Long> recordIds = records.stream()
                .map(DynamicRecord::getId)
                .collect(Collectors.toList());

        // 1. 批量查询所有字段值
        List<DynamicFieldValue> allFieldValues = fieldValueRepository.findByRecordIdIn(recordIds);
        Map<Long, List<DynamicFieldValue>> fvMap = allFieldValues.stream()
                .collect(Collectors.groupingBy(DynamicFieldValue::getRecordId));

        // 2. 批量查询所有关系
        List<DynamicRelation> allRelations = relationRepository.findBySourceRecordIdIn(recordIds);
        Map<Long, List<DynamicRelation>> relMap = allRelations.stream()
                .collect(Collectors.groupingBy(DynamicRelation::getSourceRecordId));

        // 3. 提取所有关系中的目标记录 ID，批量读取并递归填充
        List<Long> childRecordIds = allRelations.stream()
                .map(DynamicRelation::getTargetRecordId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, DynamicRecord> childRecordMap = new java.util.HashMap<>();
        if (!childRecordIds.isEmpty()) {
            List<DynamicRecord> childRecords = recordRepository.findAllById(childRecordIds);
            populateRecordsBatch(childRecords);
            childRecordMap = childRecords.stream()
                    .collect(Collectors.toMap(DynamicRecord::getId, r -> r));
        }

        // 4. 组装数据回填实体
        for (DynamicRecord record : records) {
            record.setFieldValues(fvMap.getOrDefault(record.getId(), new ArrayList<>()));

            List<DynamicRelation> rels = relMap.getOrDefault(record.getId(), new ArrayList<>());
            for (DynamicRelation rel : rels) {
                if (rel.getTargetRecordId() != null) {
                    rel.setTargetRecord(childRecordMap.get(rel.getTargetRecordId()));
                }
            }
            record.setRelations(rels);
        }
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
