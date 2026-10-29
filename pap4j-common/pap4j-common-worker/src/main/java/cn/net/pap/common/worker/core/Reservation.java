package cn.net.pap.common.worker.core;

import java.util.concurrent.CompletableFuture;

/**
 * 占座结果（{@link WorkerUtil} 内部使用，不对外暴露）。
 *
 * <p>用一个显式字段表达“本次是新建还是在途”，避免用 {@code null} 兼职充当控制信号。</p>
 *
 * @param <T>     任务返回值类型
 * @param future  交付给调用方的结果句柄
 * @param created true 表示本次新建（句柄已登记）；false 表示该 id 在途（句柄为既有）
 */
public record Reservation<T>(CompletableFuture<T> future, boolean created) {

}
