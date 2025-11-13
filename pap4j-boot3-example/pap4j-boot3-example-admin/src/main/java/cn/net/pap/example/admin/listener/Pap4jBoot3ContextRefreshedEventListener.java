package cn.net.pap.example.admin.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 *  使用规范：
 *      1、spring.factories 添加当前listener，org.springframework.context.ApplicationListener=cn.net.pap.example.admin.listener.Pap4jBoot3ContextRefreshedEventListener
 *      2、添加依赖
 *         <dependency>
 *             <groupId>org.springframework.boot</groupId>
 *             <artifactId>spring-boot-devtools</artifactId>
 *             <scope>runtime</scope>
 *             <optional>true</optional>
 *         </dependency>
 *      3、添加配置
 *         spring.devtools.restart.enabled=true
 *
 *  效果：
 *      1、如果是热重启的话，如下打印的内容是不同的
 *
 *  问题：
 *      1、热重启会出现连续的两次。 所以此次只做记录，并不配置为真实使用。
 */
public class Pap4jBoot3ContextRefreshedEventListener implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(Pap4jBoot3ContextRefreshedEventListener.class);

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 避免在父子容器中重复监听（Root ApplicationContext 的刷新）
        if (event.getApplicationContext().getParent() != null) {
            return;
        }

        ClassLoader classLoader = getClass().getClassLoader();
        boolean isHotReload = classLoader.getClass().getName().contains("RestartClassLoader");

        if (!isHotReload) {
            log.info("[Shutdown-1] 应用初始启动完成 (ContextRefreshedEvent - 首次启动)");
        } else {
            log.info("[Hot-Reload-1] 检测到应用热重启/刷新 (ContextRefreshedEvent - 热启动)");
        }
    }

}
