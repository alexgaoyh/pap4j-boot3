package cn.net.pap.example.proguard.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

public class ResponseEntityUtils {

    /**
     * Last-Modified 304
     *
     * @param isModified
     * @param body
     * @param lastModified
     * @param <T>
     * @return
     */
    public static <T> ResponseEntity<T> buildResponse(boolean isModified, T body, Instant lastModified) {
        if (!isModified) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().lastModified(lastModified).body(body);
    }


}
