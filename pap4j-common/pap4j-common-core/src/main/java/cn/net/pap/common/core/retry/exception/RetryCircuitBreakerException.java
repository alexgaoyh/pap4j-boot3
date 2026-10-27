package cn.net.pap.common.core.retry.exception;

import cn.net.pap.common.core.retry.exception.enums.PapRetryErrorEnum;

/**
 * 重试滑动窗口断路器 异常类
 */
public class RetryCircuitBreakerException extends RuntimeException {

    public RetryCircuitBreakerException(PapRetryErrorEnum message){
        super(message.toString());
    }

    public RetryCircuitBreakerException(PapRetryErrorEnum message, Throwable cause){
        super(message.toString(), cause);
    }

}
