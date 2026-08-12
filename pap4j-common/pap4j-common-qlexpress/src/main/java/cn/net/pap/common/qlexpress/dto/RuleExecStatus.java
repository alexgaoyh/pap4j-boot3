package cn.net.pap.common.qlexpress.dto;

import java.io.Serializable;

/**
 * <p>单条规则执行状态，用于加工结果审计与失败溯源。</p>
 *
 * @param targetField 规则目标字段
 * @param success     是否执行成功
 * @param errorMsg    失败原因（成功时为 null）
 */
public record RuleExecStatus(String targetField, boolean success, String errorMsg) implements Serializable {
}
