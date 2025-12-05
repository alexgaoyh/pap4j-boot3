package cn.net.pap.common.opencv.imageio;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.spi.ServiceRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ImageIO SPI 刷新工具类
 * 用于解决 Web 容器热部署或重启后，ImageIO 缓存旧 ClassLoader 的 SPI 导致的问题
 * <p>
 * 这是一个非常典型的 ClassLoader 泄漏 或 热部署缓存 问题。
 * 在 Java Web 容器（如 Tomcat）或 Spring Boot 重启/热部署场景下，IIORegistry（它是 JVM 全局单例的）往往会持有上一次部署的 ClassLoader 加载的 SPI 实例。
 * 当你重新部署时，旧的 SPI 还在注册表中，但其对应的 ClassLoader 可能已经关闭，或者指向了旧的类版本，导致报错或无法加载新格式。
 * 你需要做一个 "全量同步" (Sync) 操作：以当前线程的 ClassLoader 为准，存在的就注册，不存在（或过时）的就清理。
 */
public class ImageIOSPIHelper {

    /**
     * 刷新 ImageIO SPI
     * 逻辑：以当前 ClassLoader 能扫描到的 SPI 为准，清理掉过期的/不可用的，注册新的。
     */
    public static void refreshImageIOSPI() {
        try {
            System.out.println("=== IIORegistry Cache ===");
            dumpRegistryInfo();

            System.out.println("=== 开始刷新 ImageIO SPI (Sync Mode) ===");

            IIORegistry registry = IIORegistry.getDefaultInstance();
            ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

            if (currentClassLoader == null) {
                currentClassLoader = ClassLoader.getSystemClassLoader();
            }

            // 1. 刷新 ImageReaderSpi
            refreshCategory(registry, ImageReaderSpi.class, currentClassLoader);

            // 2. 刷新 ImageWriterSpi
            refreshCategory(registry, ImageWriterSpi.class, currentClassLoader);

            // 3. 验证结果
            verifyAndPrintStatus();

        } catch (Exception e) {
            System.err.println("刷新 ImageIO SPI 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 通用的刷新逻辑，适用于 Reader 和 Writer
     */
    private static <T> void refreshCategory(IIORegistry registry, Class<T> category, ClassLoader currentCL) {
        String categoryName = category.getSimpleName();
        System.out.println("--- 正在处理: " + categoryName + " ---");

        // 步骤 A: 扫描当前环境最新的 SPI (这是我们的"白名单")
        Map<String, T> validNewProviders = new HashMap<>();
        Iterator<T> scanIter = ServiceRegistry.lookupProviders(category, currentCL);
        while (scanIter.hasNext()) {
            T provider = scanIter.next();
            validNewProviders.put(provider.getClass().getName(), provider);
        }
        System.out.println("当前环境扫描到可用 SPI 数量: " + validNewProviders.size());

        // 步骤 B: 清理注册表中"过期"或"不存在"的 SPI
        // 注意：必须先收集要删除的对象，不能在迭代器遍历时直接 deregister，否则会报 ConcurrentModificationException
        List<T> toDeregister = new ArrayList<>();
        Iterator<T> registeredIter = registry.getServiceProviders(category, false);

        while (registeredIter.hasNext()) {
            T provider = registeredIter.next();
            String className = provider.getClass().getName();

            // 判断是否是 JDK 自带的 (Bootstrap ClassLoader 加载的为 null)
            // 我们通常不想删除 JDK 核心的 JPEG/PNG 支持，除非你确定要完全重写
            boolean isJdkInternal = provider.getClass().getClassLoader() == null;

            if (isJdkInternal) {
                continue; // 跳过 JDK 内部类，安全第一
            }

            // 核心判断：如果注册表里的这个类，在最新的扫描结果里找不到，说明它属于旧的 ClassLoader 或者已被移除
            if (!validNewProviders.containsKey(className)) {
                toDeregister.add(provider);
            } else {
                // 进阶判断：虽然类名一样，但如果 ClassLoader 不一样（旧的 Tomcat WebAppClassLoader），也得删掉换新的
                if (provider.getClass().getClassLoader() != currentCL && provider.getClass().getClassLoader() != currentCL.getParent()) {
                    // 这里比较激进，如果 ClassLoader 不匹配也认为过期
                    // 注意：根据你的容器层级，可能需要调整这个判断，但在热部署场景下通常是必要的
                    toDeregister.add(provider);
                    System.out.println("发现同名但在不同ClassLoader的SPI (视为过期): " + className);
                    // 同时确保我们要把新的那个注册进去，所以从 validNewProviders 移除它，让后续步骤 C 重新注册
                    // (不需要从map移除，因为只要 registry.deregister 之后，步骤 C 的 contains 检查就会失败，从而重新注册)
                }
            }
        }

        // 执行清理
        for (T p : toDeregister) {
            System.out.println("清理过期/失效 SPI: " + p.getClass().getName());
            registry.deregisterServiceProvider(p, category);
        }

        // 步骤 C: 注册缺失的 SPI
        int addedCount = 0;
        for (Map.Entry<String, T> entry : validNewProviders.entrySet()) {
            T provider = entry.getValue();
            // 检查是否已经注册 (这里 ServiceRegistry 会处理去重，但为了打印日志我们手动查一下)
            boolean alreadyRegistered = registry.contains(provider);

            if (!alreadyRegistered) {
                registry.registerServiceProvider(provider);
                addedCount++;
                System.out.println("注册新 SPI: " + entry.getKey());
            }
        }

        System.out.println(categoryName + " 刷新完毕: 清理了 " + toDeregister.size() + " 个, 新增了 " + addedCount + " 个");
    }

    /**
     * 验证并打印状态
     */
    private static void verifyAndPrintStatus() {
        System.out.println("\n=== 最终状态验证 ===");

        // 强制触发一次 ImageIO 的缓存重新计算
        ImageIO.scanForPlugins();

        String[] readers = ImageIO.getReaderFormatNames();
        String[] writers = ImageIO.getWriterFormatNames();

        System.out.println("可用 Reader 格式 (" + readers.length + "): " + Arrays.toString(readers));
        System.out.println("可用 Writer 格式 (" + writers.length + "): " + Arrays.toString(writers));

        if (readers.length == 0) System.err.println("警告: 没有检测到任何图片读取器！");
        if (writers.length == 0) System.err.println("警告: 没有检测到任何图片写入器！");

        // 简单的功能性验证 (可选)
        // 验证是否包含常见的 WebP 或 TIFF (如果你的项目依赖这些)
        // if (!Arrays.asList(readers).contains("webp")) { ... }
    }

    public static void dumpRegistryInfo() {
        IIORegistry registry = IIORegistry.getDefaultInstance();

        System.out.println("=== IIORegistry Diagnostic ===");

        // 统计ImageReaderSpi数量
        int readerCount = 0;
        Iterator<ImageReaderSpi> readerIterator = registry.getServiceProviders(ImageReaderSpi.class, true);

        List<ImageReaderSpi> readerSpis = new ArrayList<>();
        while (readerIterator.hasNext()) {
            ImageReaderSpi spi = readerIterator.next();
            readerSpis.add(spi);
            readerCount++;
        }

        System.out.println("Total ImageReaderSpi: " + readerCount);

        // 打印详细信息
        for (ImageReaderSpi spi : readerSpis) {
            System.out.printf("  - %s (Loader: %s)%n", spi.getClass().getName(), getClassLoaderInfo(spi.getClass().getClassLoader()));
        }

        // 同样处理ImageWriterSpi
        System.out.println("\nImageWriterSpi Providers:");
        Iterator<ImageWriterSpi> writerIterator = registry.getServiceProviders(ImageWriterSpi.class, true);

        while (writerIterator.hasNext()) {
            ImageWriterSpi spi = writerIterator.next();
            System.out.printf("  - %s (Loader: %s)%n", spi.getClass().getName(), getClassLoaderInfo(spi.getClass().getClassLoader()));
        }
    }

    private static String getClassLoaderInfo(ClassLoader loader) {
        if (loader == null) {
            return "BootstrapClassLoader";
        }
        return loader.getClass().getName() + "@" + Integer.toHexString(loader.hashCode());
    }

}