package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 外部进程调用测试.
 */
public class ExeProcessPoolUtilTest {

    private static final Logger log = LoggerFactory.getLogger(ExeProcessPoolUtilTest.class);

    /**
     * 测试调用打包生成的 exe 进程并读取输出与退出码.
     *
     * @throws Exception 进程调用或等待异常
     */
    // @Test
    public void simpleTest1() throws Exception {
        String exePath = "pap4j-common-exe.exe";
        File exeFile = new File(exePath);
        Assumptions.assumeTrue(exeFile.exists(), "EXE 文件不存在，跳过集成测试: " + exePath);

        ProcessBuilder pb = new ProcessBuilder(List.of(exePath, ""));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Process-Output] 收到: {}", line);
            }
        }
        int exitCode = process.waitFor();
        log.info("[Process-Exit] 退出码: {}", exitCode);
        Assertions.assertEquals(0, exitCode);
    }

}
