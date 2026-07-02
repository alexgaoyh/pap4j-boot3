package cn.net.pap.common.vips;

import cn.net.pap.common.vips.jna.LibVips;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 使用 JNA 调用 libvips 实现的高效图像处理器。
 *
 * @see <a href="https://libvips.github.io/libvips/">libvips 官方网站</a>
 * @see <a href="https://libvips.github.io/libvips/API/current/">libvips C API 官方文档</a>
 */
public class VipsImageProcessor {
    private static final Logger log = LoggerFactory.getLogger(VipsImageProcessor.class);

    private static volatile boolean initialized = false;
    private static volatile boolean shuttingDown = false;

    /**
     * 确保 libvips 库已初始化。
     * 在使用任何 libvips 函数前，必须先调用 vips_init。
     */
    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (VipsImageProcessor.class) {
            if (shuttingDown) {
                throw new IllegalStateException(
                        "[Vips-Init] libvips 已关闭，在同一 JVM 进程中无法再次初始化");
            }
            if (!initialized) {
                int result = LibVips.INSTANCE.vips_init("pap4j-common-vips");
                if (result != 0) {
                    throw new RuntimeException(
                            "[Vips-Init] 无法初始化 libvips，错误码: " + result);
                }
                initialized = true;
                log.info("[Vips-Init] 成功初始化 libvips");
            }
        }
    }

    /**
     * 关闭 libvips 库并释放全局资源。
     * 应仅在应用/JVM 停止时调用以防止堆外内存泄漏。
     *
     * <p><b>重要本地限制：</b></p>
     * 在 libvips 中，一旦调用了 {@code vips_shutdown()}，该进程就会永久卸载该库。
     * 在同一个操作系统进程中再次调用 {@code vips_init()} 将导致未定义行为或本地死锁。
     *
     * <p>因此：</p>
     * <ul>
     *   <li>如果同一 JVM 进程中还有其他测试类需要使用 libvips，请不要在 JUnit 的 {@code @AfterAll} 或 {@code @AfterEach} 中调用此方法。</li>
     *   <li>在生产环境（例如 Spring Boot）中，应将此方法挂载到应用上下文销毁生命周期中（例如在单例 Bean 上使用 {@code @PreDestroy}），以确保在 JVM 关闭时仅执行一次。</li>
     * </ul>
     */
    public static synchronized void shutdown() {
        if (initialized) {
            shuttingDown = true;
            try {
                LibVips.INSTANCE.vips_shutdown();
                initialized = false;
                log.info("[Vips-Shutdown] 成功关闭 libvips");
            } catch (Throwable t) {
                log.error("[Vips-Shutdown] 关闭 libvips 时发生错误: ", t);
            }
        }
    }

    /**
     * 动态将图像从一个格式转换为另一个格式。
     * 输出格式根据输出路径的后缀自动识别（例如 .webp, .png, .jpg）。
     *
     * @param inputPath  源图片文件路径
     * @param outputPath 转换后的目标图片文件路径
     * @throws IOException 如果加载或写入图像失败
     */
    public static void convertFormat(String inputPath, String outputPath) throws IOException {
        ensureInitialized();

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("找不到输入文件: " + inputPath);
        }

        // 自动创建输出文件的父目录以防止写入失败
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 清除之前的 vips 错误缓冲区
        LibVips.INSTANCE.vips_error_clear();

        // 加载图像元数据并建立只读流指针（惰性加载）
        // 注意：第二个参数是 vips 变参的 NULL 终止符
        Pointer image = LibVips.INSTANCE.vips_image_new_from_file(inputPath, (Object) null);
        if (image == null) {
            String errorMsg = LibVips.INSTANCE.vips_error_buffer();
            throw new IOException("无法从 " + inputPath + " 加载图片，错误: " + errorMsg);
        }

        try {
            // 将图像流通过处理管道直接写入到目标文件（惰性求值与执行）
            // 注意：第三个参数是 vips 变参的 NULL 终止符
            int result = LibVips.INSTANCE.vips_image_write_to_file(image, outputPath, (Object) null);
            if (result != 0) {
                String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                throw new IOException("无法将图片写入 " + outputPath + "，错误: " + errorMsg);
            }
        } finally {
            // 显式释放 C 语言本地对象以防堆外内存泄漏
            LibVips.GLib.INSTANCE.g_object_unref(image);
            // 释放线程级内存缓存
            LibVips.INSTANCE.vips_thread_shutdown();
        }
    }
}
