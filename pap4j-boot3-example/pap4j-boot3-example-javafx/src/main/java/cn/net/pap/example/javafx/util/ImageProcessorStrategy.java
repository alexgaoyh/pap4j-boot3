package cn.net.pap.example.javafx.util;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import cn.net.pap.example.javafx.dto.ExecResult;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Strategy
 */
public interface ImageProcessorStrategy {

    Logger log = LoggerFactory.getLogger(ImageProcessorStrategy.class);

    String PATH = System.getenv("PATH");

    // 单例 CachedThreadPool 用于 IO 密集任务
    ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool();

    /**
     * 应用退出时调用
     */
    static void shutdownExecutor() {
        log.info("{}", "Already Called ImageProcessorStrategy.shutdownExecutor");
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                IO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IO_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 去除区域内
     */
    ExecResult imageRemoveIn(String inputPath, String outputPath, double x1, double y1, double x2, double y2);

    // ==========================================
    // 新增：容量限制的输出流，完美替换 ByteArrayOutputStream 防止 OOM
    // ==========================================
    class BoundedOutputStream extends OutputStream {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private final int maxSize = 5 * 1024 * 1024; // 限制最大 5MB
        private boolean truncated = false;

        @Override
        public void write(int b) {
            if (baos.size() < maxSize) {
                baos.write(b);
            } else if (!truncated) {
                writeWarning();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (baos.size() < maxSize) {
                int allowed = Math.min(len, maxSize - baos.size());
                baos.write(b, off, allowed);
                if (allowed < len && !truncated) {
                    writeWarning();
                }
            }
        }

        private void writeWarning() {
            truncated = true;
            try {
                baos.write("\n[WARNING] Output truncated due to exceeding max length.\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }

        public String toString(String charset) throws UnsupportedEncodingException {
            return baos.toString(charset);
        }

        @Override
        public void close() throws IOException {
            baos.close();
        }
    }

    /**
     * exec
     *
     * @param extra
     * @return
     */
    private static Map<String, String> mergeEnv(Map<String, String> extra) {
        // 拷贝当前进程环境（保留 PATH, HOME 等）
        Map<String, String> merged = new HashMap<>(System.getenv());
        if (extra != null && !extra.isEmpty()) {
            merged.putAll(extra); // 覆盖或添加
        }
        return merged;
    }

    private static ExecResult exec(CommandLine cmdLine, Map<String, String> envVars, File workingDir, long timeoutMs, boolean isWindows) throws IOException {

        // 使用防 OOM 的 BoundedOutputStream
        BoundedOutputStream outStream = new BoundedOutputStream();
        BoundedOutputStream errStream = new BoundedOutputStream();

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
                } catch (Exception ignored) {
                }
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
    static ExecResult execWithShell(String rawCommand, Map<String, String> extraEnv, File workingDir, long timeoutMs) throws IOException {
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
    static ExecResult execWithShell(String rawCommand, Map<String, String> extraEnv) throws IOException {
        return execWithShell(rawCommand, extraEnv, null, 10_000);
    }

    /**
     * 保存文件至临时文件夹
     *
     * @param inputPath
     * @throws IOException
     */
    static void imageSaveInTmpFolder(String inputPath) throws IOException {
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

        // zero copy
        try (FileChannel inChannel = FileChannel.open(sourceFile.toPath(), StandardOpenOption.READ);
             FileChannel outChannel = FileChannel.open(targetFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long size = inChannel.size();
            long transferred = 0;
            while (transferred < size) {
                transferred += inChannel.transferTo(transferred, size - transferred, outChannel);
            }
        }

        // 注册历史文件映射
        PathHistoryManager.registerHistoricalFile(sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
    }

}
