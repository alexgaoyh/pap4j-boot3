package cn.net.pap.example.admin.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Spring Boot 应用关闭事件监听器
 * 只实现 ApplicationListener<ContextClosedEvent> 接口
 * 通过 META-INF/spring.factories 注册
 */
public class Pap4jBoot3ContextClosedEventListener implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(Pap4jBoot3ContextClosedEventListener.class);

    /**
     * 处理应用上下文关闭事件
     * 这是最主要的关闭事件监听方法
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("[Shutdown-1] 应用上下文开始关闭 (ContextClosedEvent)");

        // 执行关闭清理工作
        performShutdownCleanup();

        log.info("[Shutdown-1] 应用上下文关闭完成");

    }

    /**
     * 执行主要的关闭清理工作
     */
    private void performShutdownCleanup() {
        try {
            log.info("开始执行关闭清理流程...");

            // 阶段1: 停止业务服务
            log.info("→ 停止业务服务");
            stopBusinessServices();

            // 阶段2: 释放资源
            log.info("→ 释放系统资源");
            releaseResources();

            // 阶段3: 关闭连接池
            log.info("→ 关闭数据库连接池");
            closeConnectionPools();

            log.info("关闭清理流程完成");

        } catch (Exception e) {
            log.error("关闭清理过程中发生异常", e);
        }
    }

    /**
     * 停止业务服务
     */
    private void stopBusinessServices() {
        // 停止:
        // - 定时任务
        // - 消息监听
        // - 业务处理线程
        // - 其他后台服务
    }

    /**
     * 释放系统资源
     */
    private void releaseResources() {
        // 释放:
        // - 文件句柄
        // - 网络连接
        // - 内存缓存
        // - 临时文件
    }

    /**
     * 关闭连接池
     */
    private void closeConnectionPools() {
        // 关闭:
        // - 数据库连接池 (HikariCP, Druid等)
        // - Redis 连接池
        // - HTTP 连接池
        // - 其他外部服务连接
    }
}