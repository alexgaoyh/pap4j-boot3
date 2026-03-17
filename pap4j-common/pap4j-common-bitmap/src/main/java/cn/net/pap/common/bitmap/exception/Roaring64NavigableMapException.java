package cn.net.pap.common.bitmap.exception;

/**
 * <p><strong>Roaring64NavigableMap 自定义异常类</strong></p>
 * <p>该类继承自 {@code RuntimeException}，用于处理与 {@code Roaring64NavigableMap} 相关的运行时异常。</p>
 * <ul>
 * <li>主要用于在序列化、反序列化等操作中抛出特定的错误信息。</li>
 * </ul>
 * 
 * @author alexgaoyh
 */
public class Roaring64NavigableMapException extends RuntimeException {

    /**
     * serialVersionUID :
     * <p>序列化版本控制标识符。</p>
     */
    private static final long serialVersionUID = -7479182840398184195L;

    /**
     * <p>构造一个新的异常实例，带有指定的详细错误信息。</p>
     * 
     * @param message 详细的错误信息
     */
    public Roaring64NavigableMapException(String message){
        super(message);
    }

    /**
     * <p>构造一个新的异常实例，带有指定的详细错误信息和导致该异常的原因。</p>
     * 
     * @param message 详细的错误信息
     * @param cause 导致该异常的具体原因（一个 {@code Throwable} 对象）
     */
    public Roaring64NavigableMapException(String message, Throwable cause){
        super(message,cause);
    }

}
