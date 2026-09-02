package cn.net.pap.common.vips;

import cn.net.pap.common.vips.jna.LibVips;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 使用 JNA 调用 libvips 实现的高效图像处理器。
 *
 * <p><b>JNA 本地内存安全设计规约（后续编写/新增代码必须严格遵循）：</b></p>
 * <ul>
 *   <li><b>1. 防提前回收 (Prevent Early GC)</b>：任何由 Java 分配并传入 C 本地代码的内存缓冲（如 {@link com.sun.jna.Memory}
 *       或 Direct {@link java.nio.ByteBuffer}），若后续无 Java 级显式读写，其生命周期极易被 JIT 编译器判定为结束而被 GC 回收。
 *       这会导致底层 C 代码异步/惰性处理时访问野指针崩溃。必须在 C 处理函数全部执行完毕后，显式调用
 *       {@code java.lang.ref.Reference.reachabilityFence(memoryObj)} 筑起生命周期屏障。</li>
 *   <li><b>2. 强入参校验 (Strict Input Validation)</b>：必须在 Java 边界对所有的路径、字节数组、格式后缀进行非空、非零长度等校验，
 *       绝不允许把 Null 指针或非法参数直接传给 C 接口。</li>
 *   <li><b>3. 线程级资源清理 (Per-Thread Cleanup)</b>：由于 libvips 采用激进的 Per-thread 内存池与错误缓存，而 Java 常驻于 Web
 *       线程池中。调用完毕后，必须在 {@code finally} 中显式执行 {@code LibVips.INSTANCE.vips_thread_shutdown()} 清理临时状态，防止线程复用导致的堆外内存泄漏。</li>
 *   <li><b>4. 生命周期防御 (Lifecycle Defense)</b>：libvips 的初始化与关闭具有 JVM 进程内单次有效性。必须确保 {@code vips_init}
 *       和 {@code vips_shutdown} 在进程生命周期内只被安全调用一次。</li>
 * </ul>
 *
 * @see <a href="https://libvips.github.io/libvips/">libvips 官方网站</a>
 * @see <a href="https://libvips.github.io/libvips/API/current/">libvips C API 官方文档</a>
 */
public class VipsImageProcessor {
    private static final Logger log = LoggerFactory.getLogger(VipsImageProcessor.class);

    private static final double SCALE_TOLERANCE = 1e-4;
    // TODO: 注意：由于超大图片处理时会产生大量本地缓存，默认使用当前用户的临时目录。如果 C 盘空间不足，建议设置环境变量 PAP_VIPS_TEMP_DIR 重定向到空间充足的磁盘分区。
    private static final String ENV_TEMP_DIR_KEY = "PAP_VIPS_TEMP_DIR";
    private static final int ENV_OVERWRITE_TRUE = 1;

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
                // 自动重定向本地 Temp 目录至 D 盘，防止 C 盘空间不足导致的大图转码写出失败
                configureNativeTempDirectory();

                // 开启 JNA 崩溃保护拦截机制。防止底层段错误（SIGSEGV）导致整个 JVM 进程崩溃闪退。
                // 开启后，段错误将被转换为可捕获的 java.lang.Error (Invalid memory access)。
                try {
                    com.sun.jna.Native.setProtected(true);
                } catch (Throwable t) {
                    log.warn("[Vips-Init] 开启 JNA 崩溃保护拦截机制失败，当前系统或 JVM 可能不支持保护模式。详情: ", t);
                }

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

    /**
     * 在内存中直接将图像从一个格式转换为另一个格式（无需读写本地磁盘文件）。
     * 输出图像格式由目标后缀指定。
     *
     * @param inputBytes   源图片字节数组
     * @param outputFormat 转换后的目标图片格式后缀（例如 "webp", "png", "jpg"）
     * @return 转换后的目标图片字节数组
     * @throws IOException 如果加载或写入图像失败
     */
    public static byte[] convertFormat(byte[] inputBytes, String outputFormat) throws IOException {
        ensureInitialized();

        if (inputBytes == null || inputBytes.length == 0) {
            throw new IllegalArgumentException("输入图片字节数组不能为空");
        }
        if (outputFormat == null || outputFormat.isEmpty()) {
            throw new IllegalArgumentException("目标图片格式不能为空");
        }

        // 清除之前的 vips 错误缓冲区
        LibVips.INSTANCE.vips_error_clear();

        // 1. 在 C 堆中分配一块内存并将 Java 字节拷贝进去
        com.sun.jna.Memory memInput = new com.sun.jna.Memory(inputBytes.length);
        memInput.write(0, inputBytes, 0, inputBytes.length);

        Pointer image = null;
        Pointer outPtr = null;
        PointerByReference outBufRef = new PointerByReference();
        LongByReference outSizeRef = new LongByReference();

        try {
            // 2. 从内存缓冲区加载图像元数据（建立惰性求值流指针）
            // 注意：第四个参数为 vips 变参的 NULL 终止符
            image = LibVips.INSTANCE.vips_image_new_from_buffer(memInput, inputBytes.length, null, (Object) null);
            if (image == null) {
                String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                throw new IOException("无法从内存缓冲区加载图片，错误: " + errorMsg);
            }

            // 3. 将处理流格式化写出到 C 堆分配的新缓冲区
            String suffix = outputFormat.startsWith(".") ? outputFormat : "." + outputFormat;
            int result = LibVips.INSTANCE.vips_image_write_to_buffer(image, suffix, outBufRef, outSizeRef, (Object) null);
            if (result != 0) {
                String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                throw new IOException("无法将图片写出到内存缓冲区，错误: " + errorMsg);
            }

            // 4. 将 C 堆数据提取并还原回 Java 字节数组
            outPtr = outBufRef.getValue();
            long outSize = outSizeRef.getValue();
            if (outPtr == null || outSize <= 0) {
                throw new IOException("写出缓冲区空指针或长度无效");
            }

            return outPtr.getByteArray(0, (int) outSize);
        } finally {
            // 5. 必须显式释放由 GLib 分配的输出内存段以防堆外内存泄漏
            if (outPtr != null) {
                LibVips.GLibBase.INSTANCE.g_free(outPtr);
            }
            // 6. 释放本地 C 对象引用以防内存泄漏
            if (image != null) {
                LibVips.GLib.INSTANCE.g_object_unref(image);
            }
            /*
             * 释放线程级内存缓存（防 Web 容器线程池堆外内存泄漏）
             *
             * 【原理】：libvips 为了避免多线程内存申请的锁竞争，内部采用了激进的“线程局部内存池 (Per-thread Memory Pools)”，
             *         在转换期间会为当前线程分配专属的 Native 缓冲区及错误缓冲区。
             *
             * 【作用】：由于当前 Java 代码运行在 Web 容器（如 WebFlux/Netty 或 Tomcat）的常驻线程池中，线程永不销毁。
             *         若不显式调用此方法，该线程持有的 libvips 堆外缓存将常驻内存，随着线程池被全面轮询，
             *         会导致巨大的物理内存隐式泄漏，且 JVM GC 对此无能为力。
             *         在此处调用可彻底清空当前线程在 C 层的临时状态与缓存，保障线上系统堆外内存的稳定性。
             */
            LibVips.INSTANCE.vips_thread_shutdown();
            /*
             * 7. 核心安全屏障（防 JIT 提前回收导致 JVM 崩溃）
             *
             * 【原理】：JIT 编译器在优化时，不看代码执行到哪一行，而是通过“数据流分析”查看变量后续是否被读取。
             *         在此行之前，memInput 作为 Java 变量的使命在第 2 步（传入 libvips）时就已经结束了。
             *         若无此屏障，当第 3 步进行耗时转换并触发 GC 时，GC 会判定 memInput 已死并将其回收。
             *         而 memInput(JNA Memory) 析构时会自动隐式释放底层的 C 堆内存。
             *         此时 libvips 的惰性流（image）若仍在读取该内存地址，就会访问野指针，直接导致 JVM 遭遇 SIGSEGV 崩溃。
             *
             * 【作用】：在此处显式宣告引用，强行将 memInput 的生命周期“拦截并拉长”至方法的最末尾，
             *         卡住 JIT 优化机制，确保 C 语言底层在整个生命周期内都能安全、稳定地访问该内存。
             */
            java.lang.ref.Reference.reachabilityFence(memInput);
        }
    }

    /**
     * 图像元数据实体（包含宽高）
     */
    public record ImageMetadata(int width, int height) {}

    /**
     * 惰性获取图像的元数据尺寸（高宽），无需加载像素到内存。
     *
     * @param inputPath 图像文件路径
     * @return 图像元数据 (width, height)
     * @throws IOException 如果无法读取文件或读取尺寸无效
     */
    public static ImageMetadata getImageMetadata(String inputPath) throws IOException {
        ensureInitialized();
        if (inputPath == null || inputPath.isEmpty()) {
            throw new IllegalArgumentException("输入图片路径不能为空");
        }

        LibVips.INSTANCE.vips_error_clear();
        Pointer image = LibVips.INSTANCE.vips_image_new_from_file(inputPath, (Object) null);
        if (image == null) {
            String errorMsg = LibVips.INSTANCE.vips_error_buffer();
            throw new IOException("无法从 " + inputPath + " 加载图片，错误: " + errorMsg);
        }

        try {
            int width = LibVips.INSTANCE.vips_image_get_width(image);
            int height = LibVips.INSTANCE.vips_image_get_height(image);
            if (width <= 0 || height <= 0) {
                throw new IOException("读取的图像尺寸无效: " + width + "x" + height);
            }
            return new ImageMetadata(width, height);
        } finally {
            LibVips.GLib.INSTANCE.g_object_unref(image);
            LibVips.INSTANCE.vips_thread_shutdown();
        }
    }

    /**
     * 基于 libvips 惰性求值管线，可选进行裁剪与缩放，并将最终图像转码写出到内存字节数组中。
     * 极其高效且在转码大图（如 1.8GB）时具有极佳的 JVM 堆内存安全保护。
     *
     * @param inputPath    输入源图片路径
     * @param left         裁剪左边界坐标 (如果为 null 则不裁剪)
     * @param top          裁剪上边界坐标 (如果为 null 则不裁剪)
     * @param width        裁剪宽度 (如果为 null 则不裁剪)
     * @param height       裁剪高度 (如果为 null 则不裁剪)
     * @param scale        缩放因子 (如果为 null 或接近 1.0 则不缩放)
     * @param outputFormat 目标输出格式后缀 (如 "jpg", "png")
     * @return 转换后的图像字节数组
     * @throws IOException 如果加载、裁剪、缩放或转码写出失败
     */
    public static byte[] processImage(
            String inputPath,
            Integer left, Integer top, Integer width, Integer height,
            Double scale,
            String outputFormat
    ) throws IOException {
        return processImage(inputPath, left, top, width, height, scale, "default", outputFormat);
    }

    /**
     * 基于 libvips 惰性求值管线，可选进行裁剪、缩放与质量转换（如灰度、二值化），并将最终图像转码写出到内存字节数组中。
     *
     * @param inputPath    输入源图片路径
     * @param left         裁剪左边界坐标 (如果为 null 则不裁剪)
     * @param top          裁剪上边界坐标 (如果为 null 则不裁剪)
     * @param width        裁剪宽度 (如果为 null 则不裁剪)
     * @param height       裁剪高度 (如果为 null 则不裁剪)
     * @param scale        缩放因子 (如果为 null 或接近 1.0 则不缩放)
     * @param quality      色彩质量参数 (支持 "default", "color", "gray", "bitonal")
     * @param outputFormat 目标输出格式后缀 (如 "jpg", "png")
     * @return 转换后的图像字节数组
     * @throws IOException 如果加载、裁剪、缩放或转码写出失败
     */
    public static byte[] processImage(
            String inputPath,
            Integer left, Integer top, Integer width, Integer height,
            Double scale,
            String quality,
            String outputFormat
    ) throws IOException {
        return processImage(inputPath, left, top, width, height, scale, scale, "0", quality, outputFormat);
    }

    /**
     * 基于 libvips 惰性求值管线，提供完整的裁剪、水平/垂直缩放、旋转/镜像与质量转换（如灰度、二值化）处理，并将最终图像转码写出到内存字节数组中。
     *
     * @param inputPath    输入源图片路径
     * @param left         裁剪左边界坐标 (如果为 null 则不裁剪)
     * @param top          裁剪上边界坐标 (如果为 null 则不裁剪)
     * @param width        裁剪宽度 (如果为 null 则不裁剪)
     * @param height       裁剪高度 (如果为 null 则不裁剪)
     * @param hScale       横向缩放因子 (如果为 null 则不进行缩放)
     * @param vScale       纵向缩放因子 (如果为 null 则使用与横向缩放因子相同的值以进行等比缩放；如果两者均非 null 且值不同，则进行非等比拉伸)
     * @param rotation     旋转与镜像参数 (支持 "0", "90", "180", "270" 或前缀带有 "!" 的水平镜像翻转如 "!90"，亦支持任意浮点旋转角如 "45"。
     *                     【集成注意】：在 IIIF 标准下，本参数对整图 (region=full) 旋转时会正确转换图像的物理宽高；但对于高频瓦片请求，
     *                     若传入非 0 旋转，会导致各子瓦片独立旋转导致拼接坐标错位与拉伸，瓦片预览建议设为 "0"，由客户端 Canvas 视口执行整体旋转。)
     * @param quality      色彩质量参数 (支持 "default", "color", "gray", "bitonal")
     * @param outputFormat 目标输出格式后缀 (如 "jpg", "png")
     * @return 转换后的图像字节数组
     * @throws IOException 如果加载、裁剪、缩放、旋转或转码写出失败
     */
    public static byte[] processImage(
            String inputPath,
            Integer left, Integer top, Integer width, Integer height,
            Double hScale, Double vScale,
            String rotation,
            String quality,
            String outputFormat
    ) throws IOException {
        ensureInitialized();
        if (inputPath == null || inputPath.isEmpty() || outputFormat == null || outputFormat.isEmpty()) {
            throw new IllegalArgumentException("输入图片路径或目标格式不能为空");
        }

        Pointer image = loadImage(inputPath);
        Pointer croppedImage = null;
        Pointer resizedImage = null;
        Pointer rotatedImage = null;
        Pointer qualityImage = null;
        try {
            Pointer currentImage = image;
            croppedImage = cropImageIfNeeded(currentImage, left, top, width, height);
            if (croppedImage != null) {
                currentImage = croppedImage;
            }

            resizedImage = resizeImageIfNeeded(currentImage, hScale, vScale);
            if (resizedImage != null) {
                currentImage = resizedImage;
            }

            rotatedImage = rotateImageIfNeeded(currentImage, rotation);
            if (rotatedImage != null) {
                currentImage = rotatedImage;
            }

            qualityImage = applyQualityIfNeeded(currentImage, quality);
            if (qualityImage != null) {
                currentImage = qualityImage;
            }

            return writeImageToBuffer(currentImage, outputFormat);
        } finally {
            if (qualityImage != null) {
                LibVips.GLib.INSTANCE.g_object_unref(qualityImage);
            }
            if (rotatedImage != null) {
                LibVips.GLib.INSTANCE.g_object_unref(rotatedImage);
            }
            if (resizedImage != null) {
                LibVips.GLib.INSTANCE.g_object_unref(resizedImage);
            }
            if (croppedImage != null) {
                LibVips.GLib.INSTANCE.g_object_unref(croppedImage);
            }
            LibVips.GLib.INSTANCE.g_object_unref(image);
            LibVips.INSTANCE.vips_thread_shutdown();
        }
    }

    private static Pointer applyQualityIfNeeded(Pointer currentImage, String quality) throws IOException {
        if (currentImage == null) {
            throw new IllegalArgumentException("输入图像指针不能为空");
        }
        if (quality == null || quality.isEmpty() || "default".equalsIgnoreCase(quality) || "color".equalsIgnoreCase(quality)) {
            return null;
        }

        LibVips.INSTANCE.vips_error_clear();
        if ("gray".equalsIgnoreCase(quality)) {
            PointerByReference outRef = new PointerByReference();
            // space = 1 corresponds to VIPS_INTERPRETATION_B_W (monochrome grayscale)
            int result = LibVips.INSTANCE.vips_colourspace(currentImage, outRef, 1, (Object) null);
            if (result != 0) {
                String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                throw new IOException("图像转换为灰度失败，错误: " + errorMsg);
            }
            return outRef.getValue();
        } else if ("bitonal".equalsIgnoreCase(quality)) {
            Pointer grayImage = null;
            Pointer maskImage = null;
            PointerByReference grayRef = new PointerByReference();
            PointerByReference maskRef = new PointerByReference();
            PointerByReference finalRef = new PointerByReference();
            try {
                // 1. 转为灰度图
                int r1 = LibVips.INSTANCE.vips_colourspace(currentImage, grayRef, 1, (Object) null);
                if (r1 != 0) {
                    String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                    throw new IOException("二值化前转为灰度失败，错误: " + errorMsg);
                }
                grayImage = grayRef.getValue();

                // 2. 比较运算，输出二值掩膜 (像素 > 127 为 1, 否则为 0)
                int r2 = LibVips.INSTANCE.vips_more_const1(grayImage, maskRef, 127.0, (Object) null);
                if (r2 != 0) {
                    String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                    throw new IOException("二值化阈值判定失败，错误: " + errorMsg);
                }
                maskImage = maskRef.getValue();

                // 3. 线性乘以 255.0，转换为 uchar 类型 (0/1 扩展到 0/255 亮度)
                int r3 = LibVips.INSTANCE.vips_linear1(maskImage, finalRef, 255.0, 0.0, "uchar", 1, (Object) null);
                if (r3 != 0) {
                    String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                    throw new IOException("二值化线性拉伸失败，错误: " + errorMsg);
                }
                return finalRef.getValue();
            } finally {
                // 必须释放管线中的中间图像以防止堆外泄露
                if (maskImage != null) {
                    LibVips.GLib.INSTANCE.g_object_unref(maskImage);
                }
                if (grayImage != null) {
                    LibVips.GLib.INSTANCE.g_object_unref(grayImage);
                }
            }
        }
        return null;
    }

    private static Pointer loadImage(String inputPath) throws IOException {
        LibVips.INSTANCE.vips_error_clear();
        Pointer image = LibVips.INSTANCE.vips_image_new_from_file(inputPath, (Object) null);
        if (image == null) {
            String errorMsg = LibVips.INSTANCE.vips_error_buffer();
            throw new IOException("无法从 " + inputPath + " 加载图片，错误: " + errorMsg);
        }
        return image;
    }

    private static Pointer cropImageIfNeeded(
            Pointer currentImage,
            Integer left, Integer top, Integer width, Integer height
    ) throws IOException {
        if (left != null && top != null && width != null && height != null) {
            PointerByReference cropRef = new PointerByReference();
            int cropResult = LibVips.INSTANCE.vips_crop(
                    currentImage, cropRef, left, top, width, height, (Object) null);
            if (cropResult != 0) {
                String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                throw new IOException("图像裁剪失败，错误: " + errorMsg);
            }
            return cropRef.getValue();
        }
        return null;
    }

    private static Pointer resizeImageIfNeeded(Pointer currentImage, Double hScale, Double vScale) throws IOException {
        if (hScale == null) {
            return null;
        }

        boolean hasHScale = Math.abs(hScale - 1.0) > SCALE_TOLERANCE;
        boolean hasVScale = vScale != null && Math.abs(vScale - 1.0) > SCALE_TOLERANCE;

        if (!hasHScale && !hasVScale) {
            return null;
        }

        PointerByReference resizeRef = new PointerByReference();
        int resizeResult;
        LibVips.INSTANCE.vips_error_clear();

        if (vScale == null || Math.abs(hScale - vScale) <= SCALE_TOLERANCE) {
            resizeResult = LibVips.INSTANCE.vips_resize(currentImage, resizeRef, hScale, (Object) null);
        } else {
            resizeResult = LibVips.INSTANCE.vips_resize(currentImage, resizeRef, hScale, "vscale", vScale, (Object) null);
        }

        if (resizeResult != 0) {
            String errorMsg = LibVips.INSTANCE.vips_error_buffer();
            throw new IOException("图像缩放失败，错误: " + errorMsg);
        }
        return resizeRef.getValue();
    }

    private static Pointer rotateImageIfNeeded(Pointer currentImage, String rotation) throws IOException {
        if (rotation == null || rotation.isEmpty() || "0".equals(rotation)) {
            return null;
        }

        boolean mirror = rotation.startsWith("!");
        String angleStr = mirror ? rotation.substring(1) : rotation;
        double angle = parseRotationAngle(angleStr, rotation);
        angle = ((angle % 360) + 360) % 360;

        boolean rotate = Math.abs(angle) > SCALE_TOLERANCE;
        if (!mirror && !rotate) {
            return null;
        }

        return performRotationAndMirroring(currentImage, mirror, rotate, angle);
    }

    private static double parseRotationAngle(String angleStr, String originalRotation) {
        try {
            return Double.parseDouble(angleStr);
        } catch (NumberFormatException e) {
            log.error("非法的旋转角度参数: {}", originalRotation, e);
            throw new IllegalArgumentException("非法的旋转角度参数: " + originalRotation, e);
        }
    }

    private static Pointer performRotationAndMirroring(
            Pointer currentImage, boolean mirror, boolean rotate, double angle
    ) throws IOException {
        Pointer activeImage = currentImage;
        Pointer flippedImage = null;
        Pointer rotatedImage = null;

        try {
            LibVips.INSTANCE.vips_error_clear();
            if (mirror) {
                PointerByReference flipRef = new PointerByReference();
                int flipResult = LibVips.INSTANCE.vips_flip(activeImage, flipRef, 0, (Object) null);
                if (flipResult != 0) {
                    String errorMsg = LibVips.INSTANCE.vips_error_buffer();
                    throw new IOException("图像镜像翻转失败，错误: " + errorMsg);
                }
                flippedImage = flipRef.getValue();
                activeImage = flippedImage;
            }

            if (rotate) {
                rotatedImage = applyVipsRotation(activeImage, angle);
                activeImage = rotatedImage;
            }

            if (mirror && rotate) {
                LibVips.GLib.INSTANCE.g_object_unref(flippedImage);
                return rotatedImage;
            }
            return mirror ? flippedImage : rotatedImage;

        } catch (Throwable t) {
            log.error("旋转或翻转处理失败", t);
            if (rotatedImage != null) {
                LibVips.GLib.INSTANCE.g_object_unref(rotatedImage);
            }
            if (flippedImage != null) {
                LibVips.GLib.INSTANCE.g_object_unref(flippedImage);
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("旋转或翻转处理失败", t);
        }
    }

    private static Pointer applyVipsRotation(Pointer activeImage, double angle) throws IOException {
        PointerByReference rotRef = new PointerByReference();
        int rotResult;
        if (Math.abs(angle - 90.0) <= SCALE_TOLERANCE) {
            rotResult = LibVips.INSTANCE.vips_rot(activeImage, rotRef, 1, (Object) null);
        } else if (Math.abs(angle - 180.0) <= SCALE_TOLERANCE) {
            rotResult = LibVips.INSTANCE.vips_rot(activeImage, rotRef, 2, (Object) null);
        } else if (Math.abs(angle - 270.0) <= SCALE_TOLERANCE) {
            rotResult = LibVips.INSTANCE.vips_rot(activeImage, rotRef, 3, (Object) null);
        } else {
            rotResult = LibVips.INSTANCE.vips_rotate(activeImage, rotRef, angle, (Object) null);
        }

        if (rotResult != 0) {
            String errorMsg = LibVips.INSTANCE.vips_error_buffer();
            throw new IOException("图像旋转失败，错误: " + errorMsg);
        }
        return rotRef.getValue();
    }

    private static byte[] writeImageToBuffer(Pointer currentImage, String outputFormat) throws IOException {
        PointerByReference outBufRef = new PointerByReference();
        LongByReference outSizeRef = new LongByReference();
        String suffix = outputFormat.startsWith(".") ? outputFormat : "." + outputFormat;
        int result = LibVips.INSTANCE.vips_image_write_to_buffer(
                currentImage, suffix, outBufRef, outSizeRef, (Object) null);
        if (result != 0) {
            String errorMsg = LibVips.INSTANCE.vips_error_buffer();
            throw new IOException("图片写出到缓冲区失败，错误: " + errorMsg);
        }
        Pointer outPtr = outBufRef.getValue();
        long outSize = outSizeRef.getValue();
        if (outPtr == null || outSize <= 0) {
            throw new IOException("写出缓冲区空指针或长度无效");
        }
        try {
            return outPtr.getByteArray(0, (int) outSize);
        } finally {
            LibVips.GLibBase.INSTANCE.g_free(outPtr);
        }
    }

    /**
     * 自动检测并重定向 Native 临时工作目录。
     * 优先读取 JVM 系统属性或环境变量 PAP_VIPS_TEMP_DIR 指定的路径，若未指定则使用系统默认的临时目录 (java.io.tmpdir)。
     */
    private static void configureNativeTempDirectory() {
        String targetTemp = System.getProperty(ENV_TEMP_DIR_KEY);
        if (targetTemp == null || targetTemp.trim().isEmpty()) {
            targetTemp = System.getenv(ENV_TEMP_DIR_KEY);
        }
        if (targetTemp == null || targetTemp.trim().isEmpty()) {
            targetTemp = System.getProperty("java.io.tmpdir");
        }
        File tempDir = new File(targetTemp);
        try {
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            if (tempDir.exists() && tempDir.isDirectory() && tempDir.canWrite()) {
                // 1. JVM 级临时目录重定向 (Tomcat / Spring / Java I/O)
                System.setProperty("java.io.tmpdir", targetTemp);

                // 2. GLib 级环境变量重定向 (因为 libvips 使用 g_get_tmp_dir() 解析临时目录)
                try {
                    LibVips.GLibBase.INSTANCE.g_setenv("TMP", targetTemp, true);
                    LibVips.GLibBase.INSTANCE.g_setenv("TEMP", targetTemp, true);
                    LibVips.GLibBase.INSTANCE.g_setenv("TMPDIR", targetTemp, true);
                    log.info("[Vips-Init] 已成功通过 GLib 接口将本地临时目录重定向至: {}", targetTemp);
                } catch (Throwable t) {
                    log.warn("[Vips-Init] 自动通过 GLib 设置临时目录失败: ", t);
                }

                // 3. 补充 OS 进程级环境重定向 (双重防线)
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    try {
                        WinKernel32.INSTANCE.SetEnvironmentVariableW("TMP", targetTemp);
                        WinKernel32.INSTANCE.SetEnvironmentVariableW("TEMP", targetTemp);
                        WinKernel32.INSTANCE.SetEnvironmentVariableW("TMPDIR", targetTemp);
                        log.info("[Vips-Init] 已通过 Windows Kernel32 将系统进程环境变量补充重定向");
                    } catch (Throwable t) {
                        log.debug("[Vips-Init] WinKernel32 补充设置失败: ", t);
                    }
                } else {
                    try {
                        UnixLibC.INSTANCE.setenv("TMP", targetTemp, ENV_OVERWRITE_TRUE);
                        UnixLibC.INSTANCE.setenv("TEMP", targetTemp, ENV_OVERWRITE_TRUE);
                        UnixLibC.INSTANCE.setenv("TMPDIR", targetTemp, ENV_OVERWRITE_TRUE);
                        log.info("[Vips-Init] 已通过 Unix LibC 将系统进程环境变量补充重定向");
                    } catch (Throwable t) {
                        log.debug("[Vips-Init] UnixLibC 补充设置失败: ", t);
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("[Vips-Init] 自动重定向 Native 临时目录失败，将使用系统默认临时目录。错误信息: ", t);
        }
    }

    private interface WinKernel32 extends com.sun.jna.win32.StdCallLibrary {
        WinKernel32 INSTANCE = com.sun.jna.Native.load("kernel32", WinKernel32.class);

        boolean SetEnvironmentVariableW(String lpName, String lpValue);
    }

    private interface UnixLibC extends com.sun.jna.Library {
        UnixLibC INSTANCE = com.sun.jna.Native.load("c", UnixLibC.class);

        int setenv(String name, String value, int overwrite);
    }
}
