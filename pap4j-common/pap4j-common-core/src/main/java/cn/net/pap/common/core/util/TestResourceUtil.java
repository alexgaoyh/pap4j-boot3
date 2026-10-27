package cn.net.pap.common.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 测试资源与临时文件辅助工具类.
 * <p>
 * 提供从 classpath 加载资源到临时文件、创建自清理临时文件及生成临时非存在路径等能力，
 * 适用于多模块单元测试与集成测试场景。
 * </p>
 */
public final class TestResourceUtil {

    private static final Logger log = LoggerFactory.getLogger(TestResourceUtil.class);

    private TestResourceUtil() {
        // 工具类私有构造器
    }

    /**
     * 将 classpath 路径下的资源文件提取为临时文件.
     * <p>优先使用当前线程上下文类加载器，若未找到则回退至本类及类加载器。</p>
     *
     * @param classpath 资源在 classpath 中的路径（例如 "test.pdf" 或 "/test.pdf"）
     * @return 提取生成的临时文件（虚拟机退出时自动标记删除）
     */
    public static File getFile(String classpath) {
        return getFile(null, classpath);
    }

    /**
     * 将 classpath 路径下的资源文件提取为临时文件，支持显式传入定位资源的类.
     *
     * @param clazz     用于定位资源的 Class 对象（可为空）
     * @param classpath 资源在 classpath 中的路径
     * @return 提取生成的临时文件
     */
    public static File getFile(Class<?> clazz, String classpath) {
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalArgumentException("Classpath resource path must not be null or blank");
        }
        String cleanPath = classpath.startsWith("/") ? classpath.substring(1) : classpath;
        String slashPath = "/" + cleanPath;

        try (InputStream is = resolveInputStream(clazz, slashPath, cleanPath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + classpath);
            }

            String suffix = "";
            int dotIdx = classpath.lastIndexOf('.');
            if (dotIdx > 0) {
                suffix = classpath.substring(dotIdx);
            }
            File tempFile = File.createTempFile("test_res_", suffix);
            tempFile.deleteOnExit();
            Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (Exception e) {
            log.error("加载测试资源失败: {}", classpath, e);
            throw new RuntimeException("加载测试资源失败: " + classpath, e);
        }
    }

    private static InputStream resolveInputStream(Class<?> clazz, String slashPath, String cleanPath) {
        java.io.InputStream is = null;
        if (clazz != null) {
            is = clazz.getResourceAsStream(slashPath);
            if (is == null) {
                is = clazz.getResourceAsStream(cleanPath);
            }
        }
        if (is == null) {
            ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
            if (contextCl != null) {
                is = contextCl.getResourceAsStream(cleanPath);
            }
        }
        if (is == null) {
            is = TestResourceUtil.class.getResourceAsStream(slashPath);
        }
        if (is == null) {
            is = TestResourceUtil.class.getClassLoader().getResourceAsStream(cleanPath);
        }
        return is;
    }

    /**
     * 创建一个临时文件并在 JVM 退出时自动删除.
     *
     * @param prefix 文件名前缀
     * @param suffix 文件名后缀（如 ".jpg"）
     * @return 临时文件
     * @throws Exception 创建失败异常
     */
    public static File createTempFile(String prefix, String suffix) throws Exception {
        File tempFile = File.createTempFile(prefix, suffix);
        tempFile.deleteOnExit();
        return tempFile;
    }

    /**
     * 生成一个当前临时目录下唯一且不存在的文件绝对路径（用于测试异常分支）.
     *
     * @param fileName 期望的文件名
     * @return 不存在的绝对路径字符串
     */
    public static String getNonExistentPath(String fileName) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File nonExistent = new File(tempDir, "non_existent_" + System.currentTimeMillis() + "_" + fileName);
        return nonExistent.getAbsolutePath();
    }
}
