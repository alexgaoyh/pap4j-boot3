package cn.net.pap.task.dto;

import org.springframework.http.HttpStatus;

import java.io.Serializable;

public class WebClientBodyDTO<T> implements Serializable {

    private HttpStatus code;

    private String msg;

    private T data;

    public WebClientBodyDTO() {
    }

    public WebClientBodyDTO(HttpStatus code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public HttpStatus getCode() {
        return code;
    }

    public void setCode(HttpStatus code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "WebClientBodyDTO{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                '}';
    }
}
