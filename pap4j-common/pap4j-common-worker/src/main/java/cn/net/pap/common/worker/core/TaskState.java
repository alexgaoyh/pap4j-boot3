package cn.net.pap.common.worker.core;

/**
 * 任务生命周期状态枚举。
 *
 * <p>状态机：{@code QUEUED → RUNNING → SUCCEEDED / FAILED / UNKNOWN}，
 * 其中 {@code QUEUED} 可在未被执行的情况下直接转入 {@code FAILED}（排队区已满回滚、打烊放弃）。</p>
 *
 * <p>{@code FAILED} 与 {@code UNKNOWN} 的分界是<b>「有没有拿到业务结论」</b>：
 * 业务抛了异常 → 结论已知，记 {@code FAILED}；
 * 执行到一半被打断、连账都没落下来 → 结论未知，记 {@code UNKNOWN}
 * （此时业务可能已经产生了副作用，重投必须幂等）。</p>
 */
public enum TaskState {

    /**
     * 已收单，等待厨师认领执行。
     */
    QUEUED,

    /**
     * 已被厨师认领，正在执行。
     */
    RUNNING,

    /**
     * 执行成功（终态：结论已知）。
     */
    SUCCEEDED,

    /**
     * 确定没有做成（终态：结论已知）：业务抛异常，或从未执行（排队区已满 / 打烊放弃）。
     * <p>“从未执行”的記錄会带 {@link TaskNotStartedException}——唯一可以无条件重投的一类；
     * 其余失败（业务异常、中断）都表示“执行过了”，重投必须靠业务幂等。</p>
     */
    FAILED,

    /**
     * 已中止但结论未知（终态）：在途被打断（打烊收口）、或连落终态都失败。
     * 业务可能已经生效，重投必须幂等。
     *
     * <p>注意：这是<b>终态</b>，不是“未初始化”——记录一旦落此状态就不会再变。</p>
     */
    UNKNOWN

}
