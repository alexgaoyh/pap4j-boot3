package cn.net.pap.common.datastructure.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * implements CommandLineRunner
 *
 * @Override public void run(String... args) throws Exception {
 * SPIDebugger.printAllSPIs();
 * }
 */
public class SPIDebugger {

    private static final Logger log = LoggerFactory.getLogger(SPIDebugger.class);

    private volatile static SPIDebugger singleton;

    private SPIDebugger() {

    }

    // 可以在启动类中增加后面的定义，这样也可以初始化当前方法，并进行调用. 静态变量引用目标类，触发类加载 private static final SPIDebugger SPI_DEBUGGER_LOADER = SPIDebugger.getSingleton(true);
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

    public static void printAllSPIs() {

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        log.info("ClassLoader : " + classLoader);

        try {
            var resources = classLoader.getResources("META-INF/services");
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                log.info("Scan SPI Define Menu : " + url);

                if (url.getProtocol().equals("file")) {
                    try {
                        var dir = java.nio.file.Paths.get(url.toURI());
                        try (var files = java.nio.file.Files.list(dir)) {
                            files.forEach(file -> {
                                String spiInterface = file.getFileName().toString();
                                try {
                                    Class<?> service = Class.forName(spiInterface, false, classLoader);
                                    log.info("SPI Interface : " + service.getName());
                                    printServiceLoaderImplementations(service);
                                } catch (Exception e) {
                                    System.err.println("Can't Load SPI Interface : " + spiInterface);
                                }
                            });
                        }
                    } catch (Exception e) {
                        System.err.println("Can't Load SPI Dir : " + url);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Scan META-INF/services Failed : " + e.getMessage());
        }
    }

    private static <S> void printServiceLoaderImplementations(Class<S> service) {
        ServiceLoader.load(service).stream().map(ServiceLoader.Provider::type).forEach(implClass -> {
            log.info("Implement Class : " + implClass.getName());
            log.info("ClassLoader : " + implClass.getClassLoader());
        });
    }
}
