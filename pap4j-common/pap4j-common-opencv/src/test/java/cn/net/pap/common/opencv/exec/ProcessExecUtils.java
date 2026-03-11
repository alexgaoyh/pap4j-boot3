package cn.net.pap.common.opencv.exec;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Apache Commons Exec 进程执行工具类
 * 基于 commons-exec 1.5.0 封装
 * 支持超时、环境变量、自定义工作目录。
 */
public class ProcessExecUtils {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecUtils.class);

    @Test
    public void test1() throws Exception {
        ExecResult result = execWithShell("echo Hello Commons Exec!", null, null, 2000);
        System.out.println("ExitCode: " + result.getExitCode());
        System.out.println("Output: " + result.getStdout());

        Map<String, String> env = Map.of("MY_VAR", "123");
        ProcessExecUtils.ExecResult r2 = ProcessExecUtils.execWithShell("echo %MY_VAR%", env, new File("."), 3000);
        System.out.println("STDOUT: " + r2.getStdout());

        // todo 改一下 javaHome 的路径
        String javaHome = "D:\\.jdks\\jdk-17.0.16+8";
        Map<String, String> envJavaHome = new HashMap<>();
        envJavaHome.put("JAVA_HOME", javaHome);
        String oldPath = System.getenv("PATH");
        envJavaHome.put("PATH", (javaHome + File.separator + "bin") + File.pathSeparator + oldPath);
        ProcessExecUtils.ExecResult r3 = ProcessExecUtils.execWithShell("java -version", envJavaHome, new File("."), 3000);
        System.out.println("STDOUT: " + r3.getStderr());

        // 中文乱码校验
        ExecResult r4 = execWithShell("echo 中文!", null, null, 2000);
        System.out.println("r4: " + r4.getStdout());

        ExecResult r6 = execWithShell("dir /b", null, new File("d:\\"), 2000);
        System.out.println("r6: " + r6.getStdout());

    }

    /**
     * vips call bat file
     *
     * @throws Exception
     */
    @Test
    public void test2() throws Exception {
        String userHome = System.getProperty("user.home");
        Map<String, String> envHome = new HashMap<>();
        String oldPath = System.getenv("PATH");
        envHome.put("PATH", "D:\\vips-dev-8.18\\bin" + File.pathSeparator + oldPath);

        String batPath = userHome + File.separator + "imageRemoveIn.bat";
        String filePath = userHome + File.separator + "1.tiff";
        String cmd = batPath + " " + filePath + " 200 200 2500 2500";
        ExecResult execResult = execWithShell(cmd, envHome, new File(userHome), 60000);
        System.out.println("ExitCode: " + execResult.toString());
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
        // 防OOM, 使用匿名内部类重写 write 方法，将最大输出限制为 5MB
        final int MAX_OUTPUT_SIZE = 5 * 1024 * 1024;
        ByteArrayOutputStream outStream = new ByteArrayOutputStream() {
            @Override
            public synchronized void write(byte[] b, int off, int len) {
                if (count >= MAX_OUTPUT_SIZE) return;
                super.write(b, off, Math.min(len, MAX_OUTPUT_SIZE - count));
            }

            @Override
            public synchronized void write(int b) {
                if (count < MAX_OUTPUT_SIZE) super.write(b);
            }
        };
        ByteArrayOutputStream errStream = new ByteArrayOutputStream() {
            @Override
            public synchronized void write(byte[] b, int off, int len) {
                if (count >= MAX_OUTPUT_SIZE) return;
                super.write(b, off, Math.min(len, MAX_OUTPUT_SIZE - count));
            }

            @Override
            public synchronized void write(int b) {
                if (count < MAX_OUTPUT_SIZE) super.write(b);
            }
        };

        PumpStreamHandler streamHandler = new PumpStreamHandler(outStream, errStream);

        // 使用 1.5.0 推荐的 Builder 替代 new DefaultExecutor()
        DefaultExecutor executor = DefaultExecutor.builder().get();
        if (workingDir != null) {
            executor.setWorkingDirectory(workingDir);
        }
        executor.setStreamHandler(streamHandler);

        ExecuteWatchdog watchdog = null;
        if (timeoutMs > 0) {
            watchdog = new ExecuteWatchdog(timeoutMs);
            executor.setWatchdog(watchdog);
        }

        // 注意：execute 使用 Map<String,String> 环境
        Map<String, String> envToUse = envVars; // 假设调用方已经合并了
        int exitCode;
        boolean isTimeout = false;

        try {
            log.debug("执行命令: {}", cmdLine.toString());
            exitCode = executor.execute(cmdLine, envToUse);
        } catch (ExecuteException e) {
            exitCode = e.getExitValue();
            // 超时校验与日志精确区分是进程报错还是被 Watchdog 超时强杀
            if (watchdog != null && watchdog.killedProcess()) {
                isTimeout = true;
                log.warn("命令执行超时({}ms被强杀): {}", timeoutMs, cmdLine.toString());
            } else {
                log.warn("命令执行异常退出 (ExitCode: {}): {}", exitCode, cmdLine.toString());
            }
        }

        // 避免写死 "gbk" 导致在英文版 Windows 下抛错
        String charset = isWindows ? Charset.defaultCharset().name() : StandardCharsets.UTF_8.name();
        return new ExecResult(exitCode, outStream.toString(charset), errStream.toString(charset), isTimeout);
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
        /**
         * 是否超时的标识
         */
        private final boolean isTimeout;

        public ExecResult(int exitCode, String stdout, String stderr, boolean isTimeout) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.isTimeout = isTimeout;
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

        public boolean isTimeout() {
            return isTimeout;
        }

        public boolean isSuccess() {
            return exitCode == 0 && !isTimeout;
        }

        @Override
        public String toString() {
            return "ExecResult{" +
                    "exitCode=" + exitCode +
                    ", isTimeout=" + isTimeout +
                    ", stdoutLength=" + (stdout != null ? stdout.length() : 0) +
                    ", stderrLength=" + (stderr != null ? stderr.length() : 0) +
                    '}';
        }
    }

}

