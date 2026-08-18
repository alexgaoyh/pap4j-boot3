package cn.net.pap.example.proguard.exception;

/**
 * 分表记录 CRUD 业务异常。
 *
 * <p>非法表名 / 非法 ext 列名 / 非法排序列名、id 或 data 缺失、自增 id 取回失败等均显式抛出本异常。</p>
 */
public class SplitRecordException extends RuntimeException {

    public SplitRecordException(String message) {
        super(message);
    }

    public SplitRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
