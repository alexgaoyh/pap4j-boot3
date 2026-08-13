package cn.net.pap.example.dynamic.form.dto;

import cn.net.pap.common.qlexpress.dto.RuleExecStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * <p>单条记录的执行提取结果。</p>
 *
 * @param recordIndex 记录下标（对应请求 records 的顺序，从 0 开始）
 * @param values      各规则提取到的值，键为 targetField；执行失败的规则不产出值
 * @param statuses    逐规则执行状态，与 rules 一一对应（失败时含 errorMsg）
 */
public record ExtractRecordResult(Integer recordIndex,
                                  Map<String, Object> values,
                                  List<RuleExecStatus> statuses) implements Serializable {
}
