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
