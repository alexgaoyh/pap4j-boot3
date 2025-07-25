package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
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
        ProcessBuilder processBuilder = new ProcessBuilder("magick", "identify", "-format", "%[compression]", "C:\\Users\\86181\\Desktop\\input.tif");
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

    /**
     * 假设有一张图像的尺寸是 641*424，
     * 假设有一条目标线段(图像顶部的水平线)[(0,424),(641,424)]
     * 假设有一个实际的线段(图像左侧的垂直线)[(0,0), (0, 424)]
     * 如上的方法，其实是将目标线段进行旋转和缩放处理，得到与实际线段相同效果的一张处理过的图像。    最终实际是图像逆时针旋转90°，然后再进行缩小，可以执行这个 magick 命令看到实际的效果。
     *
     * @throws Exception
     */
    @Test
    public void calAngleAndRatioTest() throws Exception {
        // 目标线段
        double x1 = 0, y1 = 424;
        double x2 = 641, y2 = 424;

        // 图像中对应的实际线段
        double x3 = 0, y3 = 0;
        double x4 = 0, y4 = 424;

        double angle = computeAngleDifference(x1, y1, x2, y2, x3, y3, x4, y4);
        double scale = computeScaleRatio(x1, y1, x2, y2, x3, y3, x4, y4);

        System.out.printf("Angle to rotate (degrees): %.2f\n", angle);
        System.out.printf("Scale ratio: %.4f\n", scale);

        generateImageMagickCommand("right.jpg", "output.jpg", angle, scale);
    }

    // 计算角度差（单位：度）
    public static double computeAngleDifference(double x1, double y1, double x2, double y2,
                                                double x3, double y3, double x4, double y4) {
        double angle1 = Math.atan2(y2 - y1, x2 - x1); // 目标线段角度
        double angle2 = Math.atan2(y4 - y3, x4 - x3); // 原始线段角度
        double angleDiff = Math.toDegrees(angle1 - angle2);
        return angleDiff;
    }

    // 计算长度比（缩放因子）
    public static double computeScaleRatio(double x1, double y1, double x2, double y2,
                                           double x3, double y3, double x4, double y4) {
        double len1 = Math.hypot(x2 - x1, y2 - y1); // 目标线段长度
        double len2 = Math.hypot(x4 - x3, y4 - y3); // 实际线段长度
        return len2 / len1;  // 修改为实际长度/目标长度
    }

    // 输出 ImageMagick 命令
    public static void generateImageMagickCommand(String inputPath, String outputPath,
                                                  double angle, double scale) {
        // 使用 %[fx:w/2],%[fx:h/2] 计算图像中心点
        String command = String.format(
                "magick %s -virtual-pixel transparent -background none " +
                        "-distort SRT \"%%[fx:w/2],%%[fx:h/2] %.4f %.2f\" %s",
                inputPath, scale, angle, outputPath
        );
        // 生成命令示例： magick right.jpg -virtual-pixel transparent -background none -distort SRT "%[fx:w/2],%[fx:h/2] 0.6615 -90.00" output.jpg
        // 如下命令可以裁剪掉多于的黑色的填充区域，重置图像的虚拟画布，确保裁剪后的图像没有多余的偏移信息. -trim +repage
        // magick right.jpg -virtual-pixel transparent -background none -distort SRT "%[fx:w/2],%[fx:h/2] 0.6615 -90.00" -trim +repage output.jpg
        System.out.println("ImageMagick command:");
        System.out.println(command);
    }

    @Test
    public void rotateTest() throws Exception {
        // 测试(100,50)逆时针旋转90°
        double[] rotated = rotatePoint(100d, 50d, 90);
        System.out.printf("旋转后坐标: (%.2f, %.2f)%n", rotated[0], rotated[1]);
    }

    /**
     * 旋转点坐标
     *
     * @param x     原始x坐标
     * @param y     原始y坐标
     * @param angle 旋转角度(度数)    正90表示逆时针旋转90°，右手坐标系
     * @return 旋转后的坐标点数组 [x', y']
     */
    public static double[] rotatePoint(double x, double y, double angle) {
        double radians = Math.toRadians(angle);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        double newX = x * cos - y * sin;
        double newY = x * sin + y * cos;

        return new double[]{newX, newY};
    }

    /**
     * 旋转点坐标(整数版本)
     *
     * @param x     原始x坐标
     * @param y     原始y坐标
     * @param angle 旋转角度(度数)
     * @return 旋转后的坐标点数组 [x', y'] (四舍五入取整)
     */
    public static int[] rotatePoint(int x, int y, double angle) {
        double[] result = rotatePoint((double) x, (double) y, angle);
        return new int[]{
                (int) Math.round(result[0]),
                (int) Math.round(result[1])
        };
    }


    @Test
    public void transformPointTest() throws Exception {
        // 原始图像尺寸
        int originalWidth = 200;
        int originalHeight = 100;

        // 旋转角度（逆时针）
        double rotationAngleDegrees = 90;

        // 缩放比例（例如 0.5 表示高度缩小一半）
        double scale = 0.5;

        // 计算旋转和缩放后的点坐标
        Point2D.Double originalPoint = new Point2D.Double(200, 100); // 右上角点 (200, 100)
        Point2D.Double transformedPoint = transformPoint(
                originalPoint,
                originalWidth,
                originalHeight,
                rotationAngleDegrees,
                scale
        );

        System.out.println("原始坐标: (" + originalPoint.x + ", " + originalPoint.y + ")");
        System.out.println("变换后坐标: (" + transformedPoint.x + ", " + transformedPoint.y + ")");
    }

    /**
     * 计算旋转和缩放后的点坐标（新坐标系，原点与原始中心重叠）
     *
     * @param point            原始点坐标（相对于图像左上角）
     * @param originalWidth    原始图像宽度
     * @param originalHeight   原始图像高度
     * @param rotationAngleDeg 旋转角度（逆时针，单位：度）
     * @param scale            缩放比例
     * @return 变换后的点坐标（新坐标系）
     */
    public static Point2D.Double transformPoint(
            Point2D.Double point,
            int originalWidth,
            int originalHeight,
            double rotationAngleDeg,
            double scale
    ) {
        // 1. 计算图像中心点（保留两位小数）
        double centerX = formatDouble(originalWidth / 2.0);
        double centerY = formatDouble(originalHeight / 2.0);

        // 2. 将点转换为相对中心点的坐标（保留两位小数）
        double relativeX = formatDouble(point.x - centerX);
        double relativeY = formatDouble(point.y - centerY);

        // 3. 旋转（保留两位小数）
        double rotationAngleRad = formatDouble(Math.toRadians(rotationAngleDeg));
        double cosTheta = formatDouble(Math.cos(rotationAngleRad));
        double sinTheta = formatDouble(Math.sin(rotationAngleRad));

        double rotatedX = formatDouble(relativeX * cosTheta - relativeY * sinTheta);
        double rotatedY = formatDouble(relativeX * sinTheta + relativeY * cosTheta);

        // 4. 缩放后的坐标（保留两位小数）
        double scaledX = formatDouble(rotatedX * scale);
        double scaledY = formatDouble(rotatedY * scale);

        // 5. 计算旋转后的图像尺寸（保留两位小数）
        double rotatedWidth = formatDouble(Math.abs(originalWidth * cosTheta) + Math.abs(originalHeight * sinTheta));
        double rotatedHeight = formatDouble(Math.abs(originalWidth * sinTheta) + Math.abs(originalHeight * cosTheta));

        // 6. 缩放后的图像尺寸（保留两位小数）
        double scaledWidth = formatDouble(rotatedWidth * scale);
        double scaledHeight = formatDouble(rotatedHeight * scale);

        // 7. 调整坐标系（保留两位小数）
        double newCenterX = formatDouble(scaledWidth / 2.0);
        double newCenterY = formatDouble(scaledHeight / 2.0);

        double adjustedX = formatDouble(scaledX + newCenterX);
        double adjustedY = formatDouble(scaledY + newCenterY);

        return new Point2D.Double(adjustedX, adjustedY);
    }

    private static final java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");

    /**
     * 格式化 double 值为两位小数
     */
    private static double formatDouble(double value) {
        return Double.parseDouble(df.format(value));
    }

}