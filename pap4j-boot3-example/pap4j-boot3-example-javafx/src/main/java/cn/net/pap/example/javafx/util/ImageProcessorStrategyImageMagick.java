package cn.net.pap.example.javafx.util;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import cn.net.pap.example.javafx.dto.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Strategy ImageMagick
 */
public class ImageProcessorStrategyImageMagick implements ImageProcessorStrategy {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessorStrategyImageMagick.class);

    static {
        try {
            // 初始化拷贝可执行命令
            getMagickPath(ApplicationProperties.getImageMagickPath());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 获取ImageMagick可执行文件路径
     * 如果文件不存在，则从resources中提取到临时目录
     */
    public static String getMagickPath(String extractDir) throws Exception {

        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        String osDir;
        String archDir;
        String executableName;

        /* ---------------- OS 判断 ---------------- */

        if (osName.contains("win")) {
            osDir = "win";
            executableName = "magick.exe";
        } else if (osName.contains("linux")) {
            osDir = "linux";
            executableName = "magick";
        } else if (osName.contains("mac")) {
            osDir = "mac";
            executableName = "magick";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }

        if ("amd64".equals(osArch) || "x86_64".equals(osArch)) {
            archDir = "x86_64";
        } else if ("aarch64".equals(osArch) || "arm64".equals(osArch)) {
            archDir = "arm64";
        } else {
            throw new UnsupportedOperationException("Only 64-bit architectures are supported, current arch: " + osArch);
        }

        String resourcePath = "magick/" + osDir + "/" + archDir + "/" + executableName;
        String tempPath = extractResourceToTemp(extractDir, resourcePath, executableName);
        if (!osDir.equals("win")) {
            setExecutablePermission(tempPath);
        }

        return tempPath;
    }

    /**
     * 从resources中提取文件到临时目录
     */
    private static String extractResourceToTemp(String executeFilePath, String resourcePath, String fileName) throws Exception {
        // 创建临时目录（如果不存在）
        Path tempDir = Paths.get(executeFilePath, "");
        Files.createDirectories(tempDir);

        // 生成目标文件路径
        Path targetPath = tempDir.resolve(fileName);

        // 如果文件已存在，直接返回
        if (Files.exists(targetPath)) {
            return targetPath.toAbsolutePath().toString();
        }

        // 从classpath读取资源
        try (InputStream inputStream = ImageProcessorStrategyImageMagick.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }

            // 复制文件到临时目录
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Extracted ImageMagick to: " + targetPath.toAbsolutePath());
            return targetPath.toAbsolutePath().toString();
        }
    }

    /**
     * 设置文件执行权限（Linux/Unix/macOS）
     */
    private static void setExecutablePermission(String filePath) throws IOException {
        File file = new File(filePath);

        if (!file.setExecutable(true)) {
            // 如果Java的setExecutable失败，尝试使用chmod命令
            try {
                // 注意：由于是交由 Shell 解析 (sh -c)，最好用双引号或单引号把 filePath 包裹起来， 防止路径中存在空格导致 chmod 将其识别为多个文件。
                String command = "chmod +x \"" + filePath + "\"";

                // 直接调用现有的静态方法，默认附带 10 秒超时控制
                ExecResult result = ImageProcessorStrategy.execWithShell(command, null);

                // 根据 ExecResult 判断执行情况 (假设 ExecResult 有类似 getExitCode 和 getErrStream 的 getter)
                if (result.getExitCode() != 0) {
                    System.err.println("Warning: Failed to set executable permission for: " + filePath
                            + " . Exit code: " + result.getExitCode()
                            + " . Error: " + result.getStdout()); // 请根据你 ExecResult 类的实际 getter 方法名进行调整
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to set executable permission: " + e.getMessage());
            }
        }

        // 验证权限
        if (!file.canExecute()) {
            throw new IOException("Cannot set executable permission for: " + filePath);
        }
    }

    @Override
    public ExecResult imageRemoveIn(String inputPath, String outputPath, double x1, double y1, double x2, double y2) {
        try {
            // 异步启动保存任务
            CompletableFuture<Void> saveTask = CompletableFuture.runAsync(() -> {
                try {
                    ImageProcessorStrategy.imageSaveInTmpFolder(inputPath);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, IO_EXECUTOR);

//            String drawCommand = String.format("rectangle %.2f,%.2f %.2f,%.2f", x1, y1, x2, y2);
//            String cmd = String.join(" ", "magick", "\"" + inputPath + "\"", "-fill", "white", "-draw", "\"" + drawCommand + "\"", "\"" + outputPath + "\"");
            // faster than before
            String drawCommand = String.format("%.0fx%.0f+%.0f+%.0f", x2 - x1, y2 - y1, x1, y1);
            String cmd = String.join(" ", "magick", "\"" + inputPath + "\"", "-region", drawCommand, "-fill", "white", "-colorize", "100", "+region", "\"" + outputPath + "\"");

            Map<String, String> envHome = new HashMap<>();
            envHome.put("PATH", ApplicationProperties.getImageMagickPath() + File.pathSeparator + PATH);

            // 等保存任务完成后再执行命令 等待 imageSaveInTmpFolder 完成
            saveTask.join();

            ExecResult execResult = ImageProcessorStrategy.execWithShell(cmd, envHome, new File("."), 60000);

            if (!execResult.isSuccess()) {
                log.error("Magick failed: {}\n{}", cmd, execResult.getStderr());
            }

            return execResult;
        } catch (Exception e) {
            log.warn("Magick command failed: {}", e);
            return new ExecResult(999, "", e.getMessage(), true);
        } finally {

        }
    }

}
