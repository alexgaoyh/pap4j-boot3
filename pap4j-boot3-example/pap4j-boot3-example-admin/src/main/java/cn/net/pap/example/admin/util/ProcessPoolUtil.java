package cn.net.pap.example.admin.util;

import cn.net.pap.example.admin.dto.ProcessResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 静态外部进程工具类
 * 注意：ExecutorService 由外部传入，调用方负责 shutdown
 */
public class ProcessPoolUtil {

    public static ProcessResult runJavaClass(String mainClass, String[] args, long timeoutSec) {
        List<String> cmd = buildJavaCommand(mainClass, args);
        return run(cmd, timeoutSec);
    }

    private static ProcessResult run(List<String> command, long timeoutSec) {
        Process process = null;
        StringBuilder out = new StringBuilder();

        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }

            if (timeoutSec > 0) {
                if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return new ProcessResult(-1, out + "\nTIMEOUT");
                }
            } else {
                process.waitFor();
            }

            return new ProcessResult(process.exitValue(), out.toString());

        } catch (Exception e) {
            return new ProcessResult(-1, out + "\nERROR: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
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

    /**
     * 判断是否 Windows 系统
     */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

}
