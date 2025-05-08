package cn.net.pap.common.opencv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * ImageMagick 环境验证 工具类
 */
public class ImageMagickEnvCheckerUtil {

    private static final Logger log = LoggerFactory.getLogger(ImageMagickEnvCheckerUtil.class);
    private static Boolean envMagickBool = null;

    static {
        checkMagickEnv();
    }

    private static void checkMagickEnv() {
        ProcessBuilder processBuilder = new ProcessBuilder("magick", "--version");
        try {
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                boolean hasValidOutput = line != null && line.toLowerCase().contains("imagemagick");

                boolean exited = process.waitFor(10, TimeUnit.SECONDS); // 可能被中断
                if (!exited) {
                    process.destroy();
                    log.warn("Magick check timed out");
                    envMagickBool = false;
                    return;
                }

                int exitCode = process.exitValue();
                envMagickBool = exited && exitCode == 0 && hasValidOutput;
                log.info("Magick check result: available={}", envMagickBool);
            }
        } catch (IOException e) {
            log.warn("Magick command not found", e);
            envMagickBool = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            log.warn("Magick check interrupted", e);
            envMagickBool = false;
        }
    }

    public static Boolean getEnvMagickBool() {
        return envMagickBool;
    }
    
}