package cn.net.pap.example.admin.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map exceptionHandler(MethodArgumentNotValidException methodArgumentNotValidException) throws Exception {
        List<FieldError> fieldErrors = methodArgumentNotValidException.getBindingResult().getFieldErrors();
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < fieldErrors.size(); i++) {
            message.append(fieldErrors.get(i).getField()).append(fieldErrors.get(i).getDefaultMessage());
            if (i < fieldErrors.size() - 1) {
                message.append(";");
            }
        }
        HashMap<Object, Object> map = new HashMap<>();
        map.put("err_msg", message.toString());
        map.put("code", "999");
        return map;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handlerMethodValidationException(HandlerMethodValidationException ex) {
        String message = "参数校验异常";
        if(ex.getAllErrors().size() > 0 && ex.getAllErrors().size() == 1) {
            message = ex.getAllErrors().get(0).getDefaultMessage();
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("err_msg", message);
        map.put("code", HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(map);
    }

}
