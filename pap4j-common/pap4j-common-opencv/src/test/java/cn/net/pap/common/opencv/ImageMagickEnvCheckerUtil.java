package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
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

    // ---- 命令汇总
    // 获取图像DPI  :  magick identify -format "%x - %y" input.jpg
    // 获取图像像素  :  magick identify -format "%w - %h" input.jpg
    // 有像素和DPI，就可以算出来图像的物理尺寸
    //
    // Canny边缘检测 : magick 20.jpg -canny  0x1+10%+30% edges.png
    // 临时命令：      magick 20.jpg -deskew 40% -verbose info:

    // 黑框逻辑，不完善. 假设图像的宽高是 597*836 ，  黑色0 -> 白色1 ， 可以使用下面的命令进行分析处理.
    // 上边缘
    //     magick input.jpg -crop 100%x1+0+0 +repage top.jpg
    //     magick top.jpg -colorspace gray -format "%[fx:mean]" info:
    // 下边缘
    //     magick input.jpg -crop 100%x1+0+826 +repage bottom.jpg
    //     magick bottom.jpg -colorspace gray -format "%[fx:mean]" info:
    // 左边缘
    //     magick input.jpg -crop 2x100%+0+0 +repage left.jpg
    //     magick left.jpg -colorspace gray -format "%[fx:mean]" info:
    // 右边缘
    //     magick input.jpg -crop 2x100%+581+0 +repage right.jpg
    //     magick right.jpg -colorspace gray -format "%[fx:mean]" info:

    // 在 windows 下，对某一个文件夹下的所有 jpg 文件进行调整 - 瘦身 , PowerShell / Linux
    // Get-ChildItem -Path "C:\Users\86181\Desktop" -Recurse -Filter "*.jpg" | ForEach-Object { magick -quality 20 $_.FullName $_.FullName }
    // find /home/ubuntu/image -type f -iname "*.jpg" -exec magick {} -quality 10 {} \;


    // @Test
    public void streamTest() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("magick", "no-exist.jpg", "no-exist-output.jpg");
        Process process = null;

        try {
            process = processBuilder.start();

            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int timeout = 30; // 超时时间(秒)
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(String.format("Process timed out after %d seconds", timeout));
            }

            int exitCode = process.exitValue();
            String stderr = errorOutput.toString().trim();

            if (exitCode != 0 && !stderr.isEmpty()) {
                // 仅消费 InputStream 防止阻塞
                try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while (stdReader.readLine() != null) {
                        // 不记录输出，只清空流
                    }
                }
                log.error("Magick convert failed with exit code {}: {}", exitCode, stderr);
                throw new RuntimeException(String.format("Process failed with exit code %d: %s", exitCode, stderr));
            } else {
                // 没有错误输出 → 读取 InputStream 作为有效输出
                StringBuilder stdOutput = new StringBuilder();
                try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = stdReader.readLine()) != null) {
                        stdOutput.append(line).append("\n");
                    }
                }
                log.info("Magick convert succeeded: {}", stdOutput.toString().trim());
            }

        } catch (IOException e) {
            log.warn("Magick command not found or execution failed", e);
            throw e;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly(); // 确保进程被终止
            }
        }
    }

}