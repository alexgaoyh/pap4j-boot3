package cn.net.pap.common.datastructure.exception;

/**
 * <h1>通用工具异常类 (Util Exception)</h1>
 * <p>自定义的非受检异常 ({@link RuntimeException}) 类，用于工具类内部的异常转译与抛出，
 * 从而避免将低级的受检异常泄露到业务层业务代码中，简化异常处理。</p>
 *
 * @author alexgaoyh
 */
public class UtilException extends RuntimeException {

    /**
     * <p>根据原始受检异常构造新的工具异常。</p>
     * <p>异常信息将继承自该原始异常的 {@link Throwable#getMessage()}。</p>
     *
     * @param e 引发异常的原始 Throwable 实例
     */
    public UtilException(Throwable e) {
        super(e.getMessage(), e);
    }

    /**
     * <p>根据指定的异常信息构造。</p>
     *
     * @param message 错误信息的描述文本
     */
    public UtilException(String message) {
        super(message);
    }

    /**
     * <p>同时指定异常信息文本与根本原因构造异常。</p>
     *
     * @param message   错误信息的描述文本
     * @param throwable 导致此异常的底层异常
     */
    public UtilException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
