package cn.net.pap.example.async.config;

import org.springframework.core.task.TaskDecorator;

public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String contextValue = ContextHolder.get();
        return () -> {
            try {
                ContextHolder.set(contextValue);
                runnable.run();
            } finally {
                ContextHolder.clear();
            }
        };
    }

}

