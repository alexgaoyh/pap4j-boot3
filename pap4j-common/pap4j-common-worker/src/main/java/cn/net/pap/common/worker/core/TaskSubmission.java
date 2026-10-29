package cn.net.pap.common.worker.core;

import java.util.concurrent.CompletableFuture;

/**
 * 任务提交响应（三态：收单 / 幂等重复 / 拒收）。
 *
 * @param <T>       任务返回值类型
 * @param accepted  是否收单并进入排队区
 * @param duplicate 是否为在途任务的重复提交（此时 future 为既有任务的句柄）
 * @param reason    结果描述（ACCEPTED / DUPLICATE / 拒收原因）
 * @param future    结果句柄，恒不为 null。
 *                  <b>注意</b>：REJECTED 分支中该句柄已处于异常完成态，
 *                  调用方应先判 {@code accepted()} / {@code duplicate()} 再取值
 */
public record TaskSubmission<T>(
        boolean accepted,
        boolean duplicate,
        String reason,
        CompletableFuture<T> future
) {

    /**
     * 构造收单响应。
     *
     * @param <T>    返回值类型
     * @param future 结果句柄
     * @return 提交响应
     */
    public static <T> TaskSubmission<T> accepted(CompletableFuture<T> future) {
        return new TaskSubmission<>(true, false, "ACCEPTED", future);
    }

    /**
     * 构造幂等重复响应。
     *
     * @param <T>            返回值类型
     * @param existingFuture 既有任务的结果句柄
     * @return 提交响应
     */
    public static <T> TaskSubmission<T> duplicate(CompletableFuture<T> existingFuture) {
        return new TaskSubmission<>(false, true, "DUPLICATE", existingFuture);
    }

    /**
     * 构造拒收响应。
     *
     * @param <T>    返回值类型
     * @param reason 拒收原因
     * @param future 已异常完成的句柄
     * @return 提交响应
     */
    public static <T> TaskSubmission<T> rejected(String reason, CompletableFuture<T> future) {
        return new TaskSubmission<>(false, false, reason, future);
    }

}
