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


}