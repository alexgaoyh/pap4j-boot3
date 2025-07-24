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
    // nohup find /home/ubuntu/image -type f -iname "*.jpg" -printf "Processing: %p\n" -exec magick {} -quality 10 {} \; > /tmp/image_convert.log 2>&1 & echo $! > /tmp/image_convert.pid

    // 在 windows 下，对图像添加水印，请注意这里是水印图像放大200%。
    // magick "input.jpg" ( "watermark.png" -resize 200%x ) -gravity southeast -composite "output.jpg"

    // 拼接 指定坐标，在特定位置拼接图像
    // magick -size 2067x3492 xc:white ( 000.jpg -geometry +0+0 ) -composite ( 001.jpg -geometry +0+1656 ) -composite ( 002.jpg -geometry +0+3320 ) -composite ( 003.png -alpha set -channel A -evaluate multiply 1 +channel -geometry +64+104 ) -composite final_output.png

    // 拼接 指定坐标，两张图像左右指定坐标拼接 右图 (right.jpg) 的坐标点 (50,150) 映射到 左图 (left.jpg) 的 (100,200) 右图 的 (250,150) 映射到 左图 的 (300,400)
    // magick left.jpg ( right.jpg -virtual-pixel transparent -background none -distort Affine "50,150 100,200  250,150 300,400" -background white -flatten ) +append result.jpg

    // 水平拼接
    // magick left.jpg ( right.jpg -virtual-pixel transparent -background none -distort Affine "0,0 0,0  621,0 621,0" -background white -flatten ) +append result.jpg

    // 旋转90度并拉伸 图像的尺寸是 641*424
    // 前两个点你已经提供： 第1对： (0, 0) → (0, 424) 第2对： (0, 424) → (641, 424) 伪造中心点： 源中心点：((0 + 0) / 2, (0 + 424) / 2) = (0, 212) 目标中心点：((0 + 641) / 2, (424 + 424) / 2) = (320.5, 424)
    // magick left.jpg -background none -set option:distort:viewport 641x424+0+0 -distort Affine "0,0 641,0  0,424 0,0  320.5,0 641,212" output.jpg

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

    // @Test
    public void tiffCompressionTest() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("magick", "identify", "-format",  "%[compression]", "C:\\Users\\86181\\Desktop\\input.tif");
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
                log.error("Magick identify format failed with exit code {}: {}", exitCode, stderr);
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
                log.info("Magick identify format succeeded: {}", stdOutput.toString().trim());
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

    // @Test
    public void batTest() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "D:\\knowledge\\add_watermark.bat", "D:\\knowledge\\input.jpg", "D:\\knowledge\\args2.jpg", "D:\\knowledge\\watermark.png");
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
                log.error("add_watermark bat failed with exit code {}: {}", exitCode, stderr);
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
                log.info("add_watermark bat operate succeeded: {}", stdOutput.toString().trim());
            }

        } catch (IOException e) {
            log.warn("add_watermark bat command not found or execution failed", e);
            throw e;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly(); // 确保进程被终止
            }
        }
    }

}