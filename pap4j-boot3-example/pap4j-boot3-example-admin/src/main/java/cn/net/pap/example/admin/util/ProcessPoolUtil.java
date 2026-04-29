package cn.net.pap.example.admin.util;

import cn.net.pap.example.admin.dto.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 建议优先使用  org.apache.commons commons-exec
 * 静态外部进程工具类
 * 注意：ExecutorService 由外部传入，调用方负责 shutdown
 */
public class ProcessPoolUtil {

    private static final Logger log = LoggerFactory.getLogger(ProcessPoolUtil.class);

    // 防止 OOM，限制最大读取日志大小（例如：最大 5MB）
    public static final int MAX_OUTPUT_LENGTH = 5 * 1024 * 1024;

    /**
     * NOT SUPPORT IN FAT JAR
     * @param mainClass  目标执行类
     * @param args       参数
     * @param timeoutSec 超时时间(秒)
     * @param executor   外部传入的线程池，用于异步读取流
     */
    @Deprecated
    public static ProcessResult runJavaClass(String mainClass, String[] args, long timeoutSec, ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ExecutorService 不能为空，必须由外部传入！");
        }
        List<String> cmd = buildJavaCommand(mainClass, args);
        return run(cmd, timeoutSec, executor);
    }

    /**
     * 执行通用的外部系统命令 (List 形式)
     * 例如: Arrays.asList("magick", "input.jpg", "-resize", "100x100", "output.jpg")
     *
     * @param command    命令列表（第一个元素是可执行程序，后面是参数）
     * @param timeoutSec 超时时间(秒)
     * @param executor   外部传入的线程池，用于异步读取流
     * @return 进程执行结果
     */
    public static ProcessResult runCommand(List<String> command, long timeoutSec, ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ExecutorService 不能为空，必须由外部传入！");
        }
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("执行命令不能为空！");
        }
        return run(command, timeoutSec, executor);
    }


    private static ProcessResult run(List<String> command, long timeoutSec, ExecutorService executor) {
        Process process = null;
        // 使用线程安全的 StringBuffer 替代 StringBuilder
        StringBuffer out = new StringBuffer();
        AtomicBoolean isOomRisk = new AtomicBoolean(false);
        // 将 streamReaderFuture 提升到外层定义，是为了在 finally 块中能够统一、安全地对其进行 cancel(true)。
        // 这样无论是正常执行结束、进程超时(timeout)、主线程被中断(InterruptedException)，还是发生其他异常，
        // 都能保证异步读取日志的线程被正确通知退出并释放，防止线程池中的 Worker 线程泄露。
        Future<?> streamReaderFuture = null;

        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final Process finalProcess = process;

            // 处理跨平台运行时的流编码问题
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String charsetName = isWindows ? System.getProperty("sun.jnu.encoding", "GBK") : "UTF-8";
            Charset charset = Charset.forName(charsetName);

            // 1. 将读取任务提交给外部传入的线程池，并获取 Future 句柄
            streamReaderFuture = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream(), charset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (out.length() < MAX_OUTPUT_LENGTH) {
                            out.append(line).append('\n');
                        } else if (!isOomRisk.get()) {
                            out.append("\n[WARNING] Output truncated due to exceeding max length.\n");
                            isOomRisk.set(true);
                        }

                        // 响应 Future.cancel(true) 发出的中断信号
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 当底层流被关闭时正常抛出异常，不再作为 Error 打印，防止日志噪音
                    log.debug("[ProcessPoolUtil] 读取流结束或被主动关闭", e);
                }
            });

            boolean isFinished;
            if (timeoutSec > 0) {
                isFinished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
                if (!isFinished) {
                    log.warn("[ProcessPoolUtil] 进程执行超时，准备强杀: {}", command);
                    return new ProcessResult(false, -1, out + "\nTIMEOUT_OR_KILLED");
                }
            } else {
                // 这个方法会响应中断并抛出 InterruptedException
                process.waitFor();
                isFinished = true;
            }

            // 3. 进程正常结束，等待读取线程稍微收尾，最多等 1 秒
            try {
                streamReaderFuture.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // 如果 1 秒还没读完（极少发生），强行取消任务，防止阻塞主线程
                streamReaderFuture.cancel(true);
            } catch (Exception ignored) {
                // 忽略 ExecutionException 和 InterruptedException
            }

            return new ProcessResult(isFinished, process.exitValue(), out.toString());

        } catch (InterruptedException e) {
            log.error("[ProcessPoolUtil] 收到主线程中断信号，正在强杀子进程...");
            // 重新设置中断状态，好让上层调用者（如线程池）知道线程已被中断
            Thread.currentThread().interrupt();
            return new ProcessResult(false, -1, out + "\nEXECUTION_INTERRUPTED");

        } catch (Exception e) {
            return new ProcessResult(false, -1, out + "\nERROR: " + e.getMessage());
        } finally {
            if (process != null) {

                if (process.isAlive()) {
                    // 递归获取所有子进程并强杀。 如果传入的命令是一个脚本（例如 sh script.sh 或 cmd /c script.bat），
                    // 脚本内部可能又启动了其他耗时的子进程（如 ffmpeg, magick 等）。 当超时发生时，普通的 process.destroyForcibly() 只会杀死外层的 shell 进程，
                    // 导致内部真正耗时的子进程变成孤儿进程(Orphan Process)在后台继续消耗 CPU 和内存。
                    process.descendants().forEach(ProcessHandle::destroyForcibly);

                    // 最后强杀直接启动的父进程本身
                    process.destroyForcibly();
                    try {
                        // 等待底层的进程真正被 OS 回收，防止僵尸进程残留
                        process.waitFor(1, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                }

                // 后关闭流，触发底层 SIGPIPE，防止进程死前挣扎向缓冲区继续写入导致挂起
                closeQuietly(process.getInputStream());
                closeQuietly(process.getOutputStream());
                closeQuietly(process.getErrorStream());

            }
            if (streamReaderFuture != null && !streamReaderFuture.isDone()) {
                streamReaderFuture.cancel(true);
            }
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static List<String> buildJavaCommand(String mainClass, String[] args) {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String cp = System.getProperty("java.class.path");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(mainClass);

        if (args != null) {
            cmd.addAll(Arrays.asList(args));
        }
        return cmd;
    }

}
