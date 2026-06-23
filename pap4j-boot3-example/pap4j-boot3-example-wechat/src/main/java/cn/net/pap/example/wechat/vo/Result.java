package cn.net.pap.example.wechat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "统一接口响应返回包装器")
public class Result<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 成功标志
	 */
	@Schema(description = "操作是否成功标识")
	private boolean success = true;

	/**
	 * 返回处理消息
	 */
	@Schema(description = "返回的提示消息或错误消息")
	private String message = "";

	/**
	 * 返回代码
	 */
	@Schema(description = "业务状态码 (200为成功)")
	private Integer code = 0;
	
	/**
	 * 返回数据对象 data
	 */
	@Schema(description = "响应数据体")
	private T result;
	
	/**
	 * 时间戳
	 */
	@Schema(description = "响应生成的时间戳 (毫秒)")
	private long timestamp = System.currentTimeMillis();

	public Result() {
	}

    /**
     * 兼容VUE3版token失效不跳转登录页面
     * @param code
     * @param message
     */
	public Result(Integer code, String message) {
		this.code = code;
		this.message = message;
	}
	
	public static<T> Result<T> success(String message) {
		Result<T> r = new Result<T>();
		r.setMessage(message);
		r.setCode(200);
		r.setSuccess(true);
		return r;
	}

	public static Result successObj(Object message) {
		Result<Object> r = new Result<Object>();
		r.setResult(message);
		r.setCode(200);
		r.setSuccess(true);
		return r;
	}

	public static<T> Result<T> error(String msg) {
		return error(500, msg);
	}

	public static<T> Result<T> error(int code, String msg) {
		Result<T> r = new Result<T>();
		r.setCode(code);
		r.setMessage(msg);
		r.setSuccess(false);
		return r;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Integer getCode() {
		return code;
	}

	public void setCode(Integer code) {
		this.code = code;
	}

	public T getResult() {
		return result;
	}

	public void setResult(T result) {
		this.result = result;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
}