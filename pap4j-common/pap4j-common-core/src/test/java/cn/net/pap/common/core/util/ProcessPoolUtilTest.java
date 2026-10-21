package cn.net.pap.common.core.util;

import cn.net.pap.common.core.dto.ProcessResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessPoolUtilTest {

    private static ExecutorService executor;

    @BeforeAll
    static void init() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterAll
    static void cleanup() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testRunEchoCommand() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> command = isWindows ? List.of("cmd.exe", "/c", "echo Hello Core Process")
                : List.of("sh", "-c", "echo Hello Core Process");

        ProcessResult result = ProcessPoolUtil.runCommand(command, 5, executor);
        assertTrue(result.isFinished(), "命令应执行完成");
        assertEquals(0, result.getExitCode(), "退出码应为 0");
        assertTrue(result.getOutput().contains("Hello Core Process"), "应输出对应回显内容");
    }
}
