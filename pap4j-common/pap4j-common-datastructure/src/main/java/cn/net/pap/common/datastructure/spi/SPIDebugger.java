package cn.net.pap.common.datastructure.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * <p><strong>SPIDebugger</strong> 是用于检查服务提供者接口 (SPI) 的单例工具类。</p>
 *
 * <p>它扫描类路径中的 <code>META-INF/services</code> 定义，并记录发现的 SPI 接口及其加载的实现。
 * 这对于诊断依赖注入和模块加载问题非常有用。</p>
 *
 * <p>通过 CommandLineRunner 集成的示例：</p>
 * <pre>{@code
 * @Override
 * public void run(String... args) throws Exception {
 *     SPIDebugger.printAllSPIs();
 * }
 * }</pre>
 */
public class SPIDebugger {

    private static final Logger log = LoggerFactory.getLogger(SPIDebugger.class);

    private volatile static SPIDebugger singleton;

    private SPIDebugger() {

    }

    /**
     * <p>获取调试器的单例实例，可选择立即触发扫描。</p>
     *
     * @param args 一个布尔标志数组；如果第一个元素为 true，则启动扫描。
     * @return <strong>SPIDebugger</strong> 单例实例。
     */
    public static SPIDebugger getSingleton(boolean... args) {
        if (singleton == null) {
            synchronized (SPIDebugger.class) {
                if (singleton == null) {
                    singleton = new SPIDebugger();
                    if(args != null && args.length == 1 && args[0] == true) {
                        printAllSPIs();
                    }
                }
            }
        }
        return singleton;
    }

    /**
     * <p>扫描并打印 <code>META-INF/services</code> 中定义的所有服务提供者接口。</p>
     * 
     * <p>迭代上下文 ClassLoader 找到的资源，并尝试使用 {@link ServiceLoader} 加载实现。
     * 记录接口和加载的实现。</p>
     */
    public static void printAllSPIs() {

        log.info("=== Scanning JPMS Module Layer SPI ===");
        try {
            ModuleLayer.boot().modules().forEach(module -> {
                var descriptor = module.getDescriptor();
                if (descriptor != null) {
                    descriptor.provides().forEach(provides -> {
                        String serviceName = provides.service();
                        log.info("JPMS SPI Interface : {} (provided by module: {})", serviceName, module.getName());
                        provides.providers().forEach(impl -> {
                            log.info("  -> Implement Class : {}", impl);
                        });
                    });
                }
            });
        } catch (Throwable t) {
            log.error("Failed to scan JPMS Module Layer SPI: {}", t.getMessage(), t);
        }

        log.info("=== Scanning Classpath META-INF/services SPI ===");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        log.info("ClassLoader : {}", classLoader);

        try {
            var resources = classLoader.getResources("META-INF/services");
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                log.info("Scan SPI Define Menu : {}", url);

                if (url.getProtocol().equals("file")) {
                    try {
                        var dir = java.nio.file.Paths.get(url.toURI());
                        try (var files = java.nio.file.Files.list(dir)) {
                            files.forEach(file -> {
                                String spiInterface = file.getFileName().toString();
                                try {
                                    Class<?> service = Class.forName(spiInterface, false, classLoader);
                                    log.info("Classpath SPI Interface : {}", service.getName());
                                    printServiceLoaderImplementations(service);
                                } catch (Exception e) {
                                    log.error("Can't Load Classpath SPI Interface : {}", spiInterface, e);
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.error("Can't Load SPI Dir : {}", url, e);
                    }
                } else if (url.getProtocol().equals("jar")) {
                    try {
                        java.net.JarURLConnection conn = (java.net.JarURLConnection) url.openConnection();
                        java.util.jar.JarFile jarFile = conn.getJarFile();
                        java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith("META-INF/services/") && name.length() > "META-INF/services/".length() && !entry.isDirectory()) {
                                String spiInterface = name.substring("META-INF/services/".length());
                                if (!spiInterface.contains("/")) {
                                    try {
                                        Class<?> service = Class.forName(spiInterface, false, classLoader);
                                        log.info("Classpath SPI Interface (from JAR): {}", service.getName());
                                        printServiceLoaderImplementations(service);
                                    } catch (Exception e) {
                                        log.error("Can't Load Classpath SPI Interface from JAR : {}", spiInterface, e);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Can't Load JAR SPI : {}", url, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Scan META-INF/services Failed : {}", e.getMessage(), e);
        }
    }

    /**
     * <p>帮助记录特定 SPI 服务类的已加载实现的辅助方法。</p>
     *
     * @param service 定义 SPI 的接口或抽象类。
     */
    private static <S> void printServiceLoaderImplementations(Class<S> service) {
        ServiceLoader.load(service).stream().map(ServiceLoader.Provider::type).forEach(implClass -> {
            log.info("Implement Class : {}", implClass.getName());
            log.info("ClassLoader : {}", implClass.getClassLoader());
        });
    }
}
