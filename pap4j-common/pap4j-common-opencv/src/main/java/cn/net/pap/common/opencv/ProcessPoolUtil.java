package cn.net.pap.common.opencv;

import cn.net.pap.common.opencv.dto.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.*;
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

        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final Process finalProcess = process;

            // 处理跨平台运行时的流编码问题
            Charset charset = Charset.defaultCharset();

            // 1. 将读取任务提交给外部传入的线程池，并获取 Future 句柄
            Future<?> streamReaderFuture = executor.submit(() -> {
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
                    log.error("[ProcessPoolUtil] 读取流异常", e);
                }
            });

            boolean isFinished;
            if (timeoutSec > 0) {
                isFinished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
                if (!isFinished) {
                    process.destroyForcibly();
                    // 2. 超时发生，取消异步读取任务（相当于 interrupt）
                    streamReaderFuture.cancel(true);
                    return new ProcessResult(-1, out + "\nTIMEOUT_OR_KILLED");
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

            return new ProcessResult(process.exitValue(), out.toString());

        } catch (InterruptedException e) {
            log.error("[ProcessPoolUtil] 收到主线程中断信号，正在强杀子进程...", e);
            if (process != null) {
                process.destroyForcibly();
            }
            // 重新设置中断状态，好让上层调用者（如线程池）知道线程已被中断
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, out + "\nEXECUTION_INTERRUPTED");

        } catch (Exception e) {
            return new ProcessResult(-1, out + "\nERROR: " + e.getMessage());
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getOutputStream());
                closeQuietly(process.getErrorStream());

                if (process.isAlive()) {
                    process.destroyForcibly();
                }
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

}
