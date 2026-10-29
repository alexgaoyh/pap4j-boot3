package cn.net.pap.common.worker.core;

import java.util.concurrent.RejectedExecutionException;

/**
 * 任务<b>从未执行</b>：排队区已满被拒，或打烊时还在排队被放弃。
 *
 * <p>它落在终态 {@link TaskState#FAILED} 上，但语义比普通失败强得多：
 * 业务闭包<b>一次都没被调用</b>，不可能产生任何副作用，因此是唯一一类<b>可以无条件重投</b>的失败。
 * 与之相对，业务异常、{@code InterruptedException} 等都意味着"执行过了"，
 * 重投必须依赖业务幂等（可能已生效）。</p>
 *
 * <p>判据靠类型本身：{@code record.error().type()} 等于本类全限定名 ⇔ 从未执行。
 * 对账时"可安全重投"的那一批，就是 {@code state == FAILED && error.type() == 本类}。</p>
 */
public class TaskNotStartedException extends RejectedExecutionException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 未执行的原因（如"排队区已满"、"打烊超时未执行"）
     */
    public TaskNotStartedException(String message) {
        super(message);
    }

}
