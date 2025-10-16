package cn.net.pap.example.admin.config.lifecyclemanager;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DefaultLifecycleManager {

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReadyEvent() {
        System.out.println("ApplicationReadyEvent");
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosedEvent() {
        System.out.println("ContextClosedEvent");
    }

}
