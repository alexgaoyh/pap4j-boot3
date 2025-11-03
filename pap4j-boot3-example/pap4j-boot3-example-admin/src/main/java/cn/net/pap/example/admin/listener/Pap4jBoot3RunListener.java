package cn.net.pap.example.admin.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.Duration;

/**
 * Spring Boot 3.x 应用启动监听器
 * <p>
 * 实现 SpringApplicationRunListener 接口，用于监听 Spring Boot 应用启动过程中的各个关键阶段。
 * 可以在应用启动的不同时间点执行自定义的初始化逻辑、监控启动过程或处理启动异常。
 * <p>
 * 注意：在 Spring Boot 3.x 中，需要在 META-INF/spring.factories 文件中注册此监听器：
 * org.springframework.boot.SpringApplicationRunListener=cn.net.pap.example.admin.listener.Pap4jBoot3RunListener
 */
public class Pap4jBoot3RunListener implements SpringApplicationRunListener {

    private static final Logger log = LoggerFactory.getLogger(Pap4jBoot3RunListener.class);

    /**
     * Spring Boot 3.x 必须的构造函数
     * <p>
     * Spring Boot 在创建 SpringApplicationRunListener 实例时会通过反射调用此构造函数。
     * 参数顺序和类型必须严格匹配，否则无法实例化。
     *
     * @param application 当前 Spring Boot 应用实例
     * @param args        应用启动时传递的命令行参数
     */
    public Pap4jBoot3RunListener(SpringApplication application, String[] args) {
        log.info("Pap4jBoot3RunListener 实例创建完成");
    }

    /**
     * 应用启动最初阶段调用
     * <p>
     * 在 SpringApplication 刚启动时调用，此时 BootstrapContext 可能还未完全初始化。
     * 适合执行一些最早的初始化工作。
     *
     * @param bootstrapContext 可配置的引导上下文
     */
    @Override
    public void starting(ConfigurableBootstrapContext bootstrapContext) {
        log.info("[1] Spring Boot 启动初期（starting）");
    }

    /**
     * 环境准备完成时调用
     * <p>
     * 当 ApplicationEnvironment 已准备完成，但在 ApplicationContext 创建之前调用。
     * 此时可以读取和修改环境配置，如配置文件、系统属性、命令行参数等。
     *
     * @param bootstrapContext 可配置的引导上下文
     * @param environment      已准备完成的应用环境配置
     */
    @Override
    public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
        log.info("[2] 环境准备完成（environmentPrepared）: active profiles = {}", String.join(",", environment.getActiveProfiles()));
    }

    /**
     * 应用上下文创建完成时调用
     * <p>
     * ApplicationContext 已创建并准备好，但尚未加载 Bean 定义。
     * 此时可以获取到 ApplicationContext 实例，但还不能获取 Bean。
     *
     * @param context 已创建的应用上下文实例
     */
    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        log.info("[3] 应用上下文创建完成（contextPrepared）");
    }

    /**
     * Bean 定义加载完成时调用
     * <p>
     * ApplicationContext 已加载所有 Bean 定义，但尚未实例化 Bean。
     * 此时可以检查或修改 Bean 定义。
     *
     * @param context 已加载 Bean 定义的应用上下文
     */
    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
        log.info("[4] Bean 定义加载完成（contextLoaded）");
    }

    /**
     * 应用上下文已刷新，应用已启动时调用
     * <p>
     * ApplicationContext 已刷新，所有单例 Bean 已实例化。
     * 此时应用已基本启动完成，但尚未执行 CommandLineRunner 和 ApplicationRunner。
     *
     * @param context   已启动的应用上下文
     * @param timeTaken 从启动开始到此刻所用的时间
     */
    @Override
    public void started(ConfigurableApplicationContext context, Duration timeTaken) {
        log.info("[5] Spring 已启动（started）: 用时 {} ms", timeTaken != null ? timeTaken.toMillis() : "未知");
    }

    /**
     * 应用完全就绪时调用
     * <p>
     * 在 started 之后调用，所有 CommandLineRunner 和 ApplicationRunner 已执行完毕。
     * 此时应用已完全就绪，可以开始处理外部请求。
     *
     * @param context   完全就绪的应用上下文
     * @param timeTaken 从启动开始到完全就绪所用的总时间
     */
    @Override
    public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
        log.info("[6] 应用完全就绪（ready）: 用时 {} ms", timeTaken != null ? timeTaken.toMillis() : "未知");
    }

    /**
     * 应用启动失败时调用
     * <p>
     * 在启动过程中的任何阶段发生异常时都会调用此方法。
     * 适合执行启动失败后的清理工作或发送告警通知。
     *
     * @param context   应用上下文（可能为 null，取决于失败发生的阶段）
     * @param exception 导致启动失败的异常
     */
    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        log.error("[X] 启动失败（failed）", exception);
    }
}