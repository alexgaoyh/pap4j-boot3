package cn.net.pap.common.boofcv.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 临时目录工具类（简化版）
 * 提供创建和使用临时目录的功能，使用后自动清理
 */
public final class TempDirUtils {

    // ========== 定义 Logger ==========
    private static final Logger log = LoggerFactory.getLogger(TempDirUtils.class);

    private TempDirUtils() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    // ========== 调试开关 ==========
    // 通过 JVM 参数 -Dtemp.dir.utils.dev=true 控制是否保留临时文件。 System.setProperty("temp.dir.utils.dev", "true");
    private static volatile boolean KEEP_TEMP_FILES = Boolean.getBoolean("temp.dir.utils.dev");

    // ========== 默认临时目录下的临时文件 ==========

    /**
     * 在默认临时目录创建临时文件，使用后自动清理
     *
     * @param prefix   文件名前缀
     * @param consumer 使用临时文件的回调
     */
    public static void withTempFile(String prefix, Consumer<Path> consumer) throws IOException {
        Path tempFile = Files.createTempFile(prefix, null);
        try {
            consumer.accept(tempFile);
        } finally {
            if (!KEEP_TEMP_FILES) {
                Files.deleteIfExists(tempFile);
            } else {
                log.info("调试模式保留临时文件: {}", tempFile.toAbsolutePath());
            }
        }
    }

    /**
     * 在默认临时目录创建临时文件，使用后自动清理并返回结果
     *
     * @param prefix   文件名前缀
     * @param function 使用临时文件的回调
     * @return 回调函数的返回值
     */
    public static <T> T withTempFile(String prefix, Function<Path, T> function) throws Exception {
        Path tempFile = Files.createTempFile(prefix, null);
        try {
            return function.apply(tempFile);
        } finally {
            if (!KEEP_TEMP_FILES) {
                Files.deleteIfExists(tempFile);
            } else {
                log.info("调试模式保留临时文件: {}", tempFile.toAbsolutePath());
            }
        }
    }

    // ========== 指定目录下的临时文件 ==========

    /**
     * 在指定目录创建临时文件，使用后自动清理
     *
     * @param dir      指定目录
     * @param prefix   文件名前缀
     * @param consumer 使用临时文件的回调
     */
    public static void withTempFile(Path dir, String prefix, Consumer<Path> consumer) throws IOException {
        ensureDirExists(dir);
        Path tempFile = Files.createTempFile(dir, prefix, null);
        try {
            consumer.accept(tempFile);
        } finally {
            if (!KEEP_TEMP_FILES) {
                Files.deleteIfExists(tempFile);
            } else {
                log.info("调试模式保留临时文件: {}", tempFile.toAbsolutePath());
            }
        }
    }

    /**
     * 在指定目录创建临时文件，使用后自动清理并返回结果
     *
     * @param dir      指定目录
     * @param prefix   文件名前缀
     * @param function 使用临时文件的回调
     * @return 回调函数的返回值
     */
    public static <T> T withTempFile(Path dir, String prefix, Function<Path, T> function) throws Exception {
        ensureDirExists(dir);
        Path tempFile = Files.createTempFile(dir, prefix, null);
        try {
            return function.apply(tempFile);
        } finally {
            if (!KEEP_TEMP_FILES) {
                Files.deleteIfExists(tempFile);
            } else {
                log.info("调试模式保留临时文件: {}", tempFile.toAbsolutePath());
            }
        }
    }

    // ========== 默认临时目录下的临时目录 ==========

    /**
     * 在默认临时目录创建临时目录，使用后自动清理
     *
     * @param prefix   目录名前缀
     * @param consumer 使用临时目录的回调
     */
    public static void withTempDir(String prefix, Consumer<Path> consumer) throws IOException {
        Path tempDir = Files.createTempDirectory(prefix);
        try {
            consumer.accept(tempDir);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * 在默认临时目录创建临时目录，使用后自动清理并返回结果
     *
     * @param prefix   目录名前缀
     * @param function 使用临时目录的回调
     * @return 回调函数的返回值
     */
    public static <T> T withTempDir(String prefix, Function<Path, T> function) throws Exception {
        Path tempDir = Files.createTempDirectory(prefix);
        try {
            return function.apply(tempDir);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    // ========== 指定目录下的临时目录 ==========

    /**
     * 在指定目录创建临时目录，使用后自动清理
     *
     * @param dir      指定目录
     * @param prefix   目录名前缀
     * @param consumer 使用临时目录的回调
     */
    public static void withTempDir(Path dir, String prefix, Consumer<Path> consumer) throws IOException {
        ensureDirExists(dir);
        Path tempDir = Files.createTempDirectory(dir, prefix);
        try {
            consumer.accept(tempDir);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * 在指定目录创建临时目录，使用后自动清理并返回结果
     *
     * @param dir      指定目录
     * @param prefix   目录名前缀
     * @param function 使用临时目录的回调
     * @return 回调函数的返回值
     */
    public static <T> T withTempDir(Path dir, String prefix, Function<Path, T> function) throws Exception {
        ensureDirExists(dir);
        Path tempDir = Files.createTempDirectory(dir, prefix);
        try {
            return function.apply(tempDir);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 确保目录存在
     */
    private static void ensureDirExists(Path dir) throws IOException {
        // 去掉多余的 Files.exists 检查，直接创建。Files.createDirectories 内部是线程安全的，如果目录已存在不会报错，避免了并发条件下的竞态问题
        Files.createDirectories(dir);
    }

    /**
     * 递归删除目录
     */
    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (KEEP_TEMP_FILES) {
            log.info("调试模式保留临时目录: {}", dir.toAbsolutePath());
            return;
        }
        if (!Files.exists(dir)) {
            return;
        }

        // 必须使用 try-with-resources 包裹 Files.walk 返回的 Stream，否则在 Linux 系统下会导致文件句柄 (File Descriptor) 泄漏，最终引发 "Too many open files"
        try (Stream<Path> walkStream = Files.walk(dir)) {
            walkStream.sorted(Comparator.reverseOrder()) // 从内向外删除
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // 忽略删除失败，避免影响主流程
                            log.warn("清理临时文件/目录失败: {}", path.toAbsolutePath(), e);
                        }
                    });
        }
    }
}