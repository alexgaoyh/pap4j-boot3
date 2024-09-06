package cn.net.pap.example.admin.config.exception;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map exceptionHandler(MethodArgumentNotValidException methodArgumentNotValidException ) throws Exception {
        List<FieldError> fieldErrors = methodArgumentNotValidException.getBindingResult().getFieldErrors();
        StringBuilder message = new StringBuilder();
        for (int i=0; i<fieldErrors.size(); i++) {
            message.append(fieldErrors.get(i).getField()).append(fieldErrors.get(i).getDefaultMessage());
            if (i < fieldErrors.size()-1) {
                message.append(";");
            }
        }
        HashMap<Object, Object> map = new HashMap<>();
        map.put("err_msg",message.toString());
        map.put("code", "999");
        return map;
    }

}
