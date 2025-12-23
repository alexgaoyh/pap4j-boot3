package cn.net.pap.example.javafx.util;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class ImageMagickUtil {

    private static final Logger log = LoggerFactory.getLogger(ImageMagickUtil.class);

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
        String resourcePath;
        String executableName;

        if (osName.contains("win")) {
            // Windows系统
            resourcePath = "magick/win/magick.exe";
            executableName = "magick.exe";
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix") || osName.contains("mac")) {
            // Linux/Unix/macOS系统
            resourcePath = "magick/linux/magick";
            executableName = "magick";
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
        }

        // 从resources中提取文件到临时目录
        String tempPath = extractResourceToTemp(extractDir, resourcePath, executableName);

        // 设置执行权限（Linux/Unix/macOS）
        if (!osName.contains("win")) {
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
        try (InputStream inputStream = ImageMagickUtil.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
                ProcessBuilder processBuilder = new ProcessBuilder("chmod", "+x", filePath);
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                process.getErrorStream().transferTo(System.err);

                if (exitCode != 0) {
                    System.err.println("Warning: Failed to set executable permission for: " + filePath + " . error： " + process.getErrorStream());
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

    private static Map<String, String> mergeEnv(Map<String, String> extra) {
        // 拷贝当前进程环境（保留 PATH, HOME 等）
        Map<String, String> merged = new HashMap<>(System.getenv());
        if (extra != null && !extra.isEmpty()) {
            merged.putAll(extra); // 覆盖或添加
        }
        return merged;
    }

    private static ExecResult exec(CommandLine cmdLine, Map<String, String> envVars, File workingDir, long timeoutMs, boolean isWindows) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();

        PumpStreamHandler streamHandler = new PumpStreamHandler(outStream, errStream);
        DefaultExecutor executor = new DefaultExecutor();
        if (workingDir != null) {
            executor.setWorkingDirectory(workingDir);
        }
        executor.setStreamHandler(streamHandler);

        ExecuteWatchdog watchdog = null;
        boolean killed = false;
        int exitCode = -1;
        try {
            if (timeoutMs > 0) {
                watchdog = new ExecuteWatchdog(timeoutMs);
                executor.setWatchdog(watchdog);
            }

            Map<String, String> envToUse = envVars;

            try {
                exitCode = executor.execute(cmdLine, envToUse);
            } catch (ExecuteException e) {
                exitCode = e.getExitValue();
                killed = watchdog != null && watchdog.killedProcess();
                // 如果是超时终止，等待流处理完成
                if (killed) {
                    try {
                        // 等待流处理线程安全停止，避免输出丢失
                        streamHandler.stop();
                    } catch (IOException ioe) {
                        // 记录或忽略流关闭异常
                    }
                }
            }
        } finally {
            if (watchdog != null) {
                try {
                    if (watchdog.isWatching()) {
                        watchdog.stop();
                    }
                } catch (Exception ignored) {}
            }
            try {
                outStream.close();
            } catch (IOException e) {
            }
            try {
                errStream.close();
            } catch (IOException e) {
            }
        }

        String charset = isWindows ? "gbk" : StandardCharsets.UTF_8.name();
        return new ExecResult(exitCode, outStream.toString(charset), errStream.toString(charset), killed);
    }

    /**
     * 自动使用 shell（/bin/sh -c 或 cmd /c）来执行 rawCommand，并且
     * 会把 System.getenv() 与你传入的 env 合并（保留 PATH 等），
     * 这样环境变量就会真正传到子进程。
     */
    public static ExecResult execWithShell(String rawCommand, Map<String, String> extraEnv, File workingDir, long timeoutMs) throws IOException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        CommandLine cmdLine;
        if (isWindows) {
            cmdLine = new CommandLine("cmd");
            cmdLine.addArgument("/c");
            // 第二个参数 false 表示不对 rawCommand 做额外的 quote/escape（让 shell 自己解析）
            cmdLine.addArgument(rawCommand, false);
        } else {
            cmdLine = new CommandLine("/bin/sh");
            cmdLine.addArgument("-c");
            cmdLine.addArgument(rawCommand, false);
        }

        Map<String, String> merged = mergeEnv(extraEnv);
        return exec(cmdLine, merged, workingDir, timeoutMs, isWindows);
    }

    // 简单包装：默认10s超时
    public static ExecResult execWithShell(String rawCommand, Map<String, String> extraEnv) throws IOException {
        return execWithShell(rawCommand, extraEnv, null, 10_000);
    }

    public static class ExecResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private boolean killed;

        public ExecResult(int exitCode, String stdout, String stderr, boolean killed) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.killed = killed;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        public boolean isKilled() {
            return killed;
        }

        @Override
        public String toString() {
            return "ExecResult{" +
                    "exitCode=" + exitCode +
                    ", stdout='" + stdout + '\'' +
                    ", stderr='" + stderr + '\'' +
                    ", killed=" + killed +
                    '}';
        }
    }

    public static ExecResult magick_imageRemoveIn(String inputPath, String outputPath, double x1, double y1, double x2, double y2) throws IOException {
        try {
            imageSaveInTmpFolder(inputPath);
            String drawCommand = String.format("rectangle %.2f,%.2f %.2f,%.2f", x1, y1, x2, y2);

            String cmd = String.join(" ", "magick", "\"" + inputPath + "\"", "-fill", "white", "-draw", "\"" + drawCommand + "\"", "\"" + outputPath + "\"");

            Map<String, String> envHome = new HashMap<>();
            String oldPath = System.getenv("PATH");
            envHome.put("PATH", ApplicationProperties.getImageMagickPath() + File.pathSeparator + oldPath);
            ExecResult execResult = execWithShell(cmd, envHome, new File("."), 60000);

            if (!execResult.isSuccess()) {
                log.error("Magick failed: {}\n{}", cmd, execResult.getStderr());
            }

            return execResult;
        } catch (IOException e) {
            log.warn("Magick command failed: {}", e);
            return new ExecResult(999, "", e.getMessage(), true);
        } finally {

        }
    }

    /**
     * 保存文件至临时文件夹
     * @param inputPath
     * @throws IOException
     */
    public static void imageSaveInTmpFolder(String inputPath) throws IOException {
        File sourceFile = new File(inputPath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            return;
        }

        File tmpDir = new File(ApplicationProperties.getImageTmpFolder());
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        long timestamp = System.currentTimeMillis();
        String newFileName = timestamp + "_" + sourceFile.getName();

        File targetFile = new File(tmpDir, newFileName);
        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        PathHistoryManager.registerHistoricalFile(sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
    }

}
