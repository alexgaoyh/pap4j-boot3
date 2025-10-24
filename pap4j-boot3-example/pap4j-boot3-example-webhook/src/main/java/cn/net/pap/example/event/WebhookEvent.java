package cn.net.pap.example.event;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class WebhookEvent {

    private String eventId;

    private String eventType;

    private Long timestamp;

    private Object data;

    private Map<String, Object> metadata;

    public WebhookEvent() {

    }

    public WebhookEvent(String eventType, Object data) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
        this.metadata = new HashMap<>();
    }

    public WebhookEvent addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
}
