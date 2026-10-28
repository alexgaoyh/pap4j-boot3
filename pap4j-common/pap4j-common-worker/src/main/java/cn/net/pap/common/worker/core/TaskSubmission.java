package cn.net.pap.common.worker.core;

import java.util.concurrent.CompletableFuture;

/**
 * 任务提交结果记录类（三态响应：收单 / 重复 / 拒绝）。
 *
 * @param <T>       任务返回值的泛型类型
 * @param accepted  是否收单成功并已进入排队区
 * @param duplicate 是否判定为重复提交
 * @param reason    提交结果描述信息
 * @param future    用于监听任务完成并取值的异步 Future 句柄
 */
public record TaskSubmission<T>(
        boolean accepted,
        boolean duplicate,
        String reason,
        CompletableFuture<T> future
) {

    /**
     * 快速构建收单成功结果。
     *
     * @param <T>    返回值类型
     * @param future 异步结果句柄
     * @return 提交响应结果
     */
    public static <T> TaskSubmission<T> accepted(CompletableFuture<T> future) {
        return new TaskSubmission<>(true, false, "ACCEPTED", future);
    }

    /**
     * 快速构建重复提交结果，返回已有任务句柄（幂等保障）。
     *
     * @param <T>            返回值类型
     * @param existingFuture 既有任务异步句柄
     * @return 提交响应结果
     */
    public static <T> TaskSubmission<T> duplicate(CompletableFuture<T> existingFuture) {
        return new TaskSubmission<>(false, true, "DUPLICATE", existingFuture);
    }

    /**
     * 快速构建拒收结果。
     *
     * @param <T>    返回值类型
     * @param reason 拒收原因
     * @param future 失败句柄
     * @return 提交响应结果
     */
    public static <T> TaskSubmission<T> rejected(String reason, CompletableFuture<T> future) {
        return new TaskSubmission<>(false, false, reason, future);
    }

}
