package cn.net.pap.example.nginx.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestTooLargeExceptionTest {

    @Test
    void testExceptionMessage() {
        RequestTooLargeException exception = new RequestTooLargeException("Payload exceeds limit");
        assertEquals("Payload exceeds limit", exception.getMessage());
    }

}
