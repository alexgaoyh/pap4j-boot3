package cn.net.pap.common.opencv;

import cn.net.pap.common.opencv.dto.ProcessResult;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ImageMagick 环境验证 工具类
 */
public class ImageMagickEnvCheckerUtilTest {

    private static final Logger log = LoggerFactory.getLogger(ImageMagickEnvCheckerUtilTest.class);
    private static Boolean envMagickBool = null;

    static {
        checkMagickEnv();
    }

    private static void checkMagickEnv() {
        // 构建检查命令
        List<String> command = Arrays.asList("magick", "--version");
        // 由于是短暂的初始化检查，分配一个专属的单线程池，用完即毁
        ExecutorService tempExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "magick-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            ProcessResult result = ProcessPoolUtil.runCommand(command, 10, tempExecutor);
            // 解析执行结果：退出码为 0，且输出内容包含 imagemagick
            boolean isSuccess = result.getExitCode() == 0;
            boolean hasValidOutput = result.getOutput() != null && result.getOutput().toLowerCase().contains("imagemagick");
            envMagickBool = isSuccess && hasValidOutput;
            if (envMagickBool) {
                log.info("Magick check result: available=true");
            } else {
                log.warn("Magick check failed or not found. ExitCode={}, Output=\n{}", result.getExitCode(), result.getOutput());
            }
        } catch (Exception e) {
            log.error("Magick environment check encountered an unexpected error", e);
            envMagickBool = false;
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdown(); // 停止接收新任务
                try {
                    // 等待 30 秒，给正在运行的任务一点时间
                    if (!tempExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                        tempExecutor.shutdownNow(); // 超时强制关闭
                    }
                } catch (InterruptedException e) {
                    log.error("关闭线程池时被中断", e);
                    tempExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static Boolean getEnvMagickBool() {
        return envMagickBool;
    }

    @Test
    public void commandTest1() {

        String[] command1 = {"echo", "Hello"};
        String[] command2 = {"echo", "World"};
        String[] command3 = {"echo1", "World"};
        String[] command4 = {"magick", "jpg.jp2", "-density", "300", "-units", " PixelsPerInch", "jpg.jpg"};

        ExecutorService tempExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "magick-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            for (String[] command : new String[][]{command1, command2, command3, command4}) {
                ProcessResult result = ProcessPoolUtil.runCommand(Arrays.stream(command).toList(), 10, tempExecutor);
                long start = System.currentTimeMillis();
                log.info("{}", (result.exitCode == 0) + " : " + (System.currentTimeMillis() - start));
            }
        } catch (Exception e) {
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdown(); // 停止接收新任务
                try {
                    // 等待 30 秒，给正在运行的任务一点时间
                    if (!tempExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                        tempExecutor.shutdownNow(); // 超时强制关闭
                    }
                } catch (InterruptedException e) {
                    log.error("关闭线程池时被中断", e);
                    tempExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Test
    public void magickTest() {
        try {
            List<String> jpgFiles = Arrays.asList(new String[]{TestResourceUtil.getFile("0.jpg").getAbsolutePath().toString()});
            for(String jpgFile : jpgFiles) {
                String command = "magick "+jpgFile.toString()+" -fill white -draw \"rectangle %[fx:w*0.80],%[fx:h*0.9] %[w],%[h]\" " + jpgFile.toString();
                magickCommand(command);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void magickCommand(String command) throws IOException, InterruptedException {
        String[] commandSplit = command.split(" ");
        ExecutorService tempExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "magick-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            ProcessResult result = ProcessPoolUtil.runCommand(Arrays.stream(commandSplit).toList(), 10, tempExecutor);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdown(); // 停止接收新任务
                try {
                    // 等待 30 秒，给正在运行的任务一点时间
                    if (!tempExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                        tempExecutor.shutdownNow(); // 超时强制关闭
                    }
                } catch (InterruptedException e) {
                    log.error("关闭线程池时被中断", e);
                    tempExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }


    // ---- 命令汇总
    // 获取图像DPI  :  magick identify -format "%x - %y" input.jpg
    // 获取图像像素  :  magick identify -format "%w - %h" input.jpg
    // 有像素和DPI，就可以算出来图像的物理尺寸
    //
    // Canny边缘检测 : magick 20.jpg -canny  0x1+10%+30% edges.png
    // 临时命令：      magick 20.jpg -deskew 40% -verbose info:
    // 不太准确的倾斜角度：      magick angle.jpg -deskew 40% -format "%[deskew:angle]" info:
    // 边缘检测后      magick edges.png -morphology thicken rectangle:3x1+0+0 enhanced_edges.png
    // houge-lines      magick enhanced_edges.png -hough-lines 100x100 hough_output.png
    // 尝试通过分析Hough变换后的图像来检测主导角度              magick hough_output.png -rotate -90 -define histogram:unique-colors=true  -format %c histogram:info:
    // 尝试通过分析Hough变化后的图像来检测主导角度(写入文件)      magick hough_output.png -define histogram:unique-colors=true -format "%c" histogram.txt
    // 尝试通过分析Hough变化后的图像来检测主导角度(写入文件)      magick hough_output.png -define histogram:unique-colors=true -format "%c" histogram:info.txt

    // 角度输出直线的起止点： magick angle.jpg -canny 0x1+10%+30% +write canny.png -background none -fill red -hough-lines 9x9+300 MVG:-

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

    // png to jp2
    // magick "input.png" -quality 35 -define jp2:rate=0.02 "input.jp2"

    // 当前图像的主色 （肯定有偏差）
    // magick input.jpg -resize 1x1! -format "%[hex:u.p{0,0}]" info:
    // 这里的 colors 可以大一点，如果从结果里面取出来最大的一个值，当做主色
    // magick input.jpg -colors 100 -format %c histogram:info:
    // 主色， 像素排序
    // magick input.jpg -format %c histogram:info: | sort /R
    // magick input.jpg -format %c histogram:info: | sort /R | findstr /n "." | findstr "^[1-5]:"

    // 图像上边缘20px的部分，填充白色
    // magick input.jpg -fill "#FFFFFF" -draw "rectangle 0,0 %[w],20" output.jpg

    // 图像 上下左右 20px 个像素的部分，填充白色
    // magick input.jpg -fill "#FFFFFF" -draw "rectangle 0,0 %[w],20" -draw "rectangle 0,%[fx:h-20] %[w],%[h]" -draw "rectangle 0,0 20,%[h]" -draw "rectangle %[fx:w-20],0 %[w],%[h]" output.jpg

    // 覆盖右下角 20% 宽度和 10% 高度的区域，给白色，类似移除左下角的那些水印的部分
    // magick input.jpg -fill white -draw "rectangle %[fx:w*0.80],%[fx:h*0.9] %[w],%[h]" output.jpg

    // 图像 预处理，生成黑白二值图像，可以应用到后面的 OCR 的功能上
    // magick ocr.jpg -colorspace Gray -threshold 70% ocr_ocr.png

    // 在将图像转换为 JPEG 格式时，自动调整压缩质量参数（Q），使得最终生成的 JPEG 文件大小尽可能接近你指定的大小（这里是 1000KB）。基于目标大小的自适应 JPEG 压缩
    // magick input.jpg -density 300 -units PixelsPerInch -define jpeg:extent=1000KB output.jpg

    // 图像的信息，这里大概是可以看到一些参数，比如 Quality
    // identify -verbose input.jpg

    // 只修改图像文件中的元数据（metadata），而不会改变图像的实际像素尺寸，命令不会对图像进行重采样（resample）。它只改变嵌入在文件中的 DPI 标签。图像的像素总量完全不变，只是操作系统和打印软件在读取这个文件时，会按照新的 DPI 值来计算它的建议打印尺寸。
    // magick input.jpg -density 300 output.jpg
    // 同时修改像素尺寸（重采样）：如果你希望同时改变 DPI 和像素尺寸（即真正地重新采样图像），你需要使用 -resample 选项，它会在更改分辨率的同时，自动按比例调整图像的像素尺寸，以保持相同的打印尺寸。
    // magick input.jpg -resample 300 output.jpg

    // 实现将大图分割成小块，方便在网页上分块加载和展示，比如如下命令 把 input.jpg 切割成多个 256x256 像素的小图，并命名为 input_000.jpg, input_001.jpg 等。
    // magick input.jpg -crop 256x256 +repage +adjoin input_%03d.jpg

    // jpg 转换的时候，需要区分 基线JPEG 和 渐进式JPEG(magick input.jpg -interlace Plane output.jpg)

    // 生成一个黄色背景，然后在左上角添加一行文字  A1图像  600DPI
    // magick -size 14031x19866 -density 600 xc:yellow -gravity NorthWest -pointsize 120 -annotate +100+100 "A1 Chart (600 DPI)" a1_600dpi_with_text.jpg

    // 左旋90度
    // magick input.jpg -rotate -90 output.jpg

    // 右旋90度
    // magick input.jpg -rotate 90 output.jpg

    // 左微旋 (逆时针旋转5度)
    // magick input.jpg -rotate -5 output.jpg

    // 右微旋 (顺时针旋转5度)
    // magick input.jpg -rotate 5 output.jpg

    // 裁剪 (裁剪为 400x300 大小，从坐标 (100,50) 开始)
    // magick input.jpg -crop 400x300+100+50 output.jpg

    // 去除区域内 (移除 200x200 区域，从 (50,50) 开始，用白色填充)
    // rectangle x1,y1 x2,y2 (x1, y1) → 矩形左上角的坐标   (x2, y2) → 矩形右下角的坐标
    // magick input.jpg -fill white -draw "rectangle 50,50 250,250" output.jpg

    // 去除区域外 （只有矩形区域 100,100-300,300 内的内容保留，其他区域变为白色）
    // magick input.jpg ( +clone -fill black -colorize 100 -fill white -draw "rectangle 100,100 300,300" ) -alpha off -compose copy_opacity -composite -background white -alpha remove output.jpg

    // 反色
    // magick input.jpg -negate output.jpg

    // 色深 (增加对比度，使颜色更浓)
    // magick input.jpg -level 0%,100%,0.8 output.jpg

    // 色浅 (降低对比度，使颜色更淡)
    // magick input.jpg -level 0%,100%,1.2 output.jpg

    // 锐化
    // magick input.jpg -sharpen 0x1.0 output.jpg

    // 柔化 (高斯模糊)
    // magick input.jpg -blur 0x1.0 output.jpg

    // 图像去噪 (使用非局部均值降噪)
    // magick input.jpg -statistic Nonpeak 3 output.jpg

    // 去黑边 (裁剪纯黑色边框)
    // magick input.jpg -bordercolor black -border 1x1 -fuzz 5% -trim output.jpg

    // 去边 (裁剪所有纯色边框)
    // magick input.jpg -trim output.jpg

    // 左白边/右白边 (为图像添加白色边框，这里以左白边为例，宽度10像素)
    // magick input.jpg -bordercolor white -border 10x0 output.jpg
    // magick input.jpg -gravity east -background white -extent 110%x100% output.jpg

    // 背景色平滑 (将背景替换为平均色)
    // magick input.jpg -background "%[pixel:p{0,0}]" -flatten output.jpg

    // 过滤底色 (去除接近白色的背景，使其透明)
    // magick input.jpg -fuzz 10% -transparent white output.jpg

    // 补齐图像 (将图像扩展为 800x600，用白色填充缺失部分)
    // magick input.jpg -background white -gravity center -extent 800x600 output.jpg

    // 字深 (通过提高对比度使文字更深)
    // magick input.jpg -level 10%,100%,1.1 output.jpg

    // 字浅 (通过降低对比度使文字更浅)

    // magick input.jpg +level 10%,100%,1.1 output.jpg

    @Test
    public void streamTest() {
        List<String> command = Arrays.asList("magick", "no-exist.jpg", "no-exist-output.jpg");
        // 由于是短暂的初始化检查，分配一个专属的单线程池，用完即毁
        ExecutorService tempExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "magick-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            ProcessResult result = ProcessPoolUtil.runCommand(command, 10, tempExecutor);
            log.info("Magick convert msg: {}", result.getOutput());
        } catch (Exception e) {
            log.error("Magick environment check encountered an unexpected error", e);
            envMagickBool = false;
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdown(); // 停止接收新任务
                try {
                    // 等待 30 秒，给正在运行的任务一点时间
                    if (!tempExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                        tempExecutor.shutdownNow(); // 超时强制关闭
                    }
                } catch (InterruptedException e) {
                    log.error("关闭线程池时被中断", e);
                    tempExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Test
    public void tiffCompressionTest() throws IOException, InterruptedException {
        List<String> command = Arrays.asList("magick", "identify", "-format", "%[compression]", TestResourceUtil.getFile("input.tiff").getAbsolutePath());
        // 由于是短暂的初始化检查，分配一个专属的单线程池，用完即毁
        ExecutorService tempExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "magick-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            ProcessResult result = ProcessPoolUtil.runCommand(command, 10, tempExecutor);
            log.info("Magick convert msg: {}", result.getOutput());
        } catch (Exception e) {
            log.error("Magick environment check encountered an unexpected error", e);
            envMagickBool = false;
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdown(); // 停止接收新任务
                try {
                    // 等待 30 秒，给正在运行的任务一点时间
                    if (!tempExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                        tempExecutor.shutdownNow(); // 超时强制关闭
                    }
                } catch (InterruptedException e) {
                    log.error("关闭线程池时被中断", e);
                    tempExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Test
    public void batTest() throws IOException, InterruptedException {
        List<String> command = Arrays.asList(
                "cmd.exe", "/c",
                "D:\\knowledge\\add_watermark.bat",
                "D:\\knowledge\\input.jpg",
                "D:\\knowledge\\args2.jpg",
                "D:\\knowledge\\watermark.png"
        );

        ExecutorService tempExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "magick-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            ProcessResult result = ProcessPoolUtil.runCommand(command, 10, tempExecutor);
            log.info("add_watermark bat operate succeeded: {}", result.getOutput().trim());
        } catch (Exception e) {
            log.error("add_watermark bat failed", e);
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdown(); // 停止接收新任务
                try {
                    // 等待 30 秒，给正在运行的任务一点时间
                    if (!tempExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                        tempExecutor.shutdownNow(); // 超时强制关闭
                    }
                } catch (InterruptedException e) {
                    log.error("关闭线程池时被中断", e);
                    tempExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
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

        log.info(String.format("Angle to rotate (degrees): %.2f\n", angle));
        log.info(String.format("Scale ratio: %.4f\n", scale));

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
        log.info("{}", "ImageMagick command:");
        log.info("{}", command);
    }

    @Test
    public void rotateTest() throws Exception {
        // 测试(100,50)逆时针旋转90°
        double[] rotated = rotatePoint(100d, 50d, 90);
        log.info(String.format("旋转后坐标: (%.2f, %.2f)%n", rotated[0], rotated[1]));
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

        log.info("{}", "原始坐标: (" + originalPoint.x + ", " + originalPoint.y + ")");
        log.info("{}", "变换后坐标: (" + transformedPoint.x + ", " + transformedPoint.y + ")");
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