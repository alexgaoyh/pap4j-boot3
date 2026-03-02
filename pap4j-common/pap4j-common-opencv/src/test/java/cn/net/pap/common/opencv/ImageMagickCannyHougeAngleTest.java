package cn.net.pap.common.opencv;

import cn.net.pap.common.opencv.dto.ProcessResult;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用 ImageMagick 实现图像的倾斜角度的计算
 * 拿着 hough-lines 来进行分析，分析过程包含当前图像是横版还是竖版，然后算出来主要的图像信息，然后分析倾斜角度。
 */
public class ImageMagickCannyHougeAngleTest {

    /**
     * 图像的倾斜角度
     * todo 注意这里 hough-lines 后面的这个 300 , 可以根据图像的宽高做一个比例， 然后实际的代码中，可以从大到小， 然后慢慢的算一个输出.
     * magick angle.jpg -canny 0x1+10%+30% +write canny.png -background none -fill red -hough-lines 9x9+300 MVG:-
     */
    // @Test
    public void test1() throws Exception {
        String s = angleInfoStrList("C:\\Users\\86181\\Desktop\\angle.jpg", "C:\\Users\\86181\\Desktop\\angle_canny.jpg", 300);
        System.out.println(getDetailedAnalysis(s));
    }

    // @Test
    public void test2() throws Exception {
        String s = angleInfoStrList("C:\\Users\\86181\\Desktop\\0.jpg", "C:\\Users\\86181\\Desktop\\0_canny.jpg", 300);
        drawHoughLinesJava("C:\\Users\\86181\\Desktop\\0.jpg", "C:\\Users\\86181\\Desktop\\0_lines.jpg", s, Color.RED, 2f);
    }

    /**
     * 文本方向
     * ImageMagick 配合 Java ， 实现文字方向的检测，这里只检测 水平 / 垂直
     * 思路： 判断 hough lines 的方向，来作为文字方向的判断依据
     *
     * 另外一种思路： 将图像进行水平和垂直的投影，然后判断行与列的起伏，看哪一个起伏大（方差、标准差 大 ），因为会存在字与字之间的空白行，所以会造成起伏大。
     *  如下两个命令，将图像进行水平投影和垂直投影，然后可以判断这个起伏的大小：先生成两张图许，然后做统计分析，最终做出来判断。
     *  magick edges.png -threshold 50% -negate -resize 1x\! col_projection.png
     *  magick edges.png -threshold 50% -negate -resize x1\! row_projection.png
     *  magick row_projection.png -format "%[fx:standard_deviation^2]" info:
     *  magick col_projection.png -format "%[fx:standard_deviation^2]" info:
     * @throws Exception
     */
    // @Test
    public void test3() throws Exception {
        String s = angleInfoStrList("C:\\Users\\86181\\Desktop\\1.jpg", "C:\\Users\\86181\\Desktop\\1_canny.jpg", 300);
        Direction dir = detectTextDirection(s);
        System.out.println("文本方向: " + dir.getDesc());
    }

    public static String angleInfoStrList(String inputPath, String tmpCannyPath, Integer minLength) throws Exception {
        List<String> command  = Arrays.asList(
                "magick",
                inputPath,
                "-canny", "0x1+10%+30%",
                "+write", tmpCannyPath,
                "-background", "none",
                "-fill", "red",
                "-hough-lines", "9x9+" + minLength,
                "MVG:-"
        );
        ExecutorService tempExecutor = Executors.newSingleThreadExecutor();
        try {
            ProcessResult result = ProcessPoolUtil.runCommand(command, 10, tempExecutor);
            System.out.println(result);
            return result.getOutput();
        } catch (Exception e) {
            return "false";
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdownNow();
            }
        }
    }

    /**
     * 解析Hough输出并返回所有直线的倾斜角度（根据图像方向智能处理）
     * 正值表示顺时针旋转，负值表示逆时针旋转
     */
    public static List<Double> getTiltAngles(String houghOutput) {
        List<Double> angles = new ArrayList<>();
        String[] lines = houghOutput.split("\n");

        // 先解析图像尺寸判断方向
        int[] imageSize = parseImageSize(houghOutput);
        int width = imageSize[0];
        int height = imageSize[1];
        boolean isPortrait = height > width; // 竖版图像

        for (String line : lines) {
            if (line.startsWith("line")) {
                // 提取直线端点坐标
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    try {
                        // 解析第一个点
                        String[] point1 = parts[1].split(",");
                        double x1 = Double.parseDouble(point1[0]);
                        double y1 = Double.parseDouble(point1[1]);

                        // 解析第二个点
                        String[] point2 = parts[2].split(",");
                        double x2 = Double.parseDouble(point2[0]);
                        double y2 = Double.parseDouble(point2[1]);

                        // 根据图像方向计算角度
                        double angle;
                        if (isPortrait) {
                            angle = calculateAngleForPortrait(x1, y1, x2, y2);
                        } else {
                            angle = calculateAngleForLandscape(x1, y1, x2, y2);
                        }

                        angles.add(angle);

                    } catch (Exception e) {
                        // 忽略解析错误
                        System.err.println("解析错误: " + line);
                    }
                }
            }
        }
        return angles;
    }

    /**
     * 将 Hough 输出的直线画到原图上
     *
     * @param inputPath 原图路径
     * @param outputPath 输出图路径
     * @param houghOutput ImageMagick Hough MVG 输出内容
     * @param lineColor 绘制线条颜色
     * @param lineWidth 绘制线条宽度
     * @throws IOException
     */
    public static void drawHoughLinesJava(String inputPath, String outputPath, String houghOutput,
                                          Color lineColor, float lineWidth) throws IOException {

        // 读取原图
        BufferedImage image = ImageIO.read(new File(inputPath));
        Graphics2D g2d = image.createGraphics();

        // 设置抗锯齿和线条属性
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(lineColor);
        g2d.setStroke(new BasicStroke(lineWidth));

        // 解析 Hough 输出
        List<Double[]> lines = parseHoughLines(houghOutput);

        // 绘制每条直线
        for (Double[] line : lines) {
            double x1 = line[0], y1 = line[1], x2 = line[2], y2 = line[3];
            g2d.drawLine((int) Math.round(x1), (int) Math.round(y1),
                    (int) Math.round(x2), (int) Math.round(y2));
        }

        g2d.dispose();
        // 保存生成的新图
        ImageIO.write(image, "png", new File(outputPath));
    }

    public enum Direction {
        HORIZONTAL("横向"),
        VERTICAL("纵向"),
        UNKNOWN("未知");

        private final String desc;

        Direction(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }

    // 解析 Hough 输出，返回方向
    public static Direction detectTextDirection(String houghOutput) {
        List<Double> angles = parseAngles(houghOutput);

        if (angles.isEmpty()) {
            return Direction.UNKNOWN;
        }

        int horizontalCount = 0;
        int verticalCount = 0;

        for (double angle : angles) {
            // 标准化到 0~180
            double a = Math.abs(angle) % 180;

            if (isVertical(a)) {
                verticalCount++;
            } else if (isHorizontal(a)) {
                horizontalCount++;
            }
        }

        if (horizontalCount > verticalCount) {
            return Direction.HORIZONTAL;
        } else if (verticalCount > horizontalCount) {
            return Direction.VERTICAL;
        } else {
            return Direction.UNKNOWN;
        }
    }

    // 解析每一行的 angle 值
    private static List<Double> parseAngles(String houghOutput) {
        List<Double> angles = new ArrayList<>();
        Pattern pattern = Pattern.compile("line\\s+\\d+,\\d+\\s+\\d+,\\d+\\s+#\\s+\\d+\\s+([\\-\\d\\.]+)");
        Matcher matcher = pattern.matcher(houghOutput);

        while (matcher.find()) {
            try {
                angles.add(Double.parseDouble(matcher.group(1)));
            } catch (NumberFormatException ignored) {}
        }
        return angles;
    }

    // 判断是否是竖直方向 (接近 0° 或 180°)
    private static boolean isVertical(double angle) {
        return (angle < 15 || angle > 165);
    }

    // 判断是否是水平方向 (接近 90°)
    private static boolean isHorizontal(double angle) {
        return (angle > 75 && angle < 105);
    }

    private static List<Double[]> parseHoughLines(String houghOutput) {
        List<Double[]> lines = new java.util.ArrayList<>();
        String[] outputLines = houghOutput.split("\n");
        for (String line : outputLines) {
            line = line.trim();
            if (line.startsWith("line")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    try {
                        String[] p1 = parts[1].split(",");
                        String[] p2 = parts[2].split(",");
                        double x1 = Double.parseDouble(p1[0]);
                        double y1 = Double.parseDouble(p1[1]);
                        double x2 = Double.parseDouble(p2[0]);
                        double y2 = Double.parseDouble(p2[1]);
                        lines.add(new Double[]{x1, y1, x2, y2});
                    } catch (Exception e) {
                        System.err.println("解析 Hough 线失败: " + line);
                    }
                }
            }
        }
        return lines;
    }

    /**
     * 计算横版图像中直线的角度（相对于水平方向）
     * 正值表示顺时针旋转，负值表示逆时针旋转
     */
    private static double calculateAngleForLandscape(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;

        // 计算角度（弧度转度数）
        double angleRad = Math.atan2(dy, dx);
        double angleDeg = Math.toDegrees(angleRad);

        // 规范化到 [-90, 90] 范围
        if (angleDeg > 90) angleDeg -= 180;
        if (angleDeg < -90) angleDeg += 180;

        // 让正值表示顺时针旋转，负值表示逆时针旋转
        return -angleDeg;
    }

    /**
     * 计算竖版图像中直线的角度（相对于垂直方向）
     * 正值表示顺时针旋转，负值表示逆时针旋转
     */
    private static double calculateAngleForPortrait(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;

        // 计算相对于垂直方向的角度（dx和dy交换位置）
        double angleRad = Math.atan2(dx, dy);
        double angleDeg = Math.toDegrees(angleRad);

        // 规范化到 [-45, 45] 范围
        if (angleDeg > 45) angleDeg -= 90;
        if (angleDeg < -45) angleDeg += 90;

        // 让正值表示顺时针旋转，负值表示逆时针旋转
        return -angleDeg;
    }

    /**
     * 从Hough输出中解析图像尺寸
     */
    public static int[] parseImageSize(String houghOutput) {
        String[] lines = houghOutput.split("\n");
        for (String line : lines) {
            if (line.startsWith("viewbox")) {
                // 提取viewbox中的宽高
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    try {
                        int width = Integer.parseInt(parts[3]);
                        int height = Integer.parseInt(parts[4]);
                        return new int[]{width, height};
                    } catch (Exception e) {
                        System.err.println("解析图像尺寸错误: " + line);
                    }
                }
            }
        }
        return new int[]{0, 0}; // 默认值
    }

    /**
     * 直接获取图像平均倾斜角度
     */
    public static double getImageSkewAngle(String houghOutput) {
        List<Double> angles = getTiltAngles(houghOutput);
        if (angles.isEmpty()) return 0.0;

        // 计算平均值
        double sum = 0;
        for (double angle : angles) {
            sum += angle;
        }
        return sum / angles.size();
    }

    /**
     * 获取图像倾斜角度并给出明确描述
     */
    public static String getSkewDescription(String houghOutput) {
        double skewAngle = getImageSkewAngle(houghOutput);
        int[] size = parseImageSize(houghOutput);
        boolean isPortrait = size[1] > size[0];

        String imageType = isPortrait ? "竖版" : "横版";
        String direction = (skewAngle > 0) ? "顺时针" : "逆时针";

        return String.format("%s图像倾斜: %.2f° (%s旋转)",
                imageType, Math.abs(skewAngle), direction);
    }

    /**
     * 获取详细的倾斜分析报告
     */
    public static String getDetailedAnalysis(String houghOutput) {
        List<Double> angles = getTiltAngles(houghOutput);
        double skewAngle = getImageSkewAngle(houghOutput);
        int[] size = parseImageSize(houghOutput);
        boolean isPortrait = size[1] > size[0];

        StringBuilder report = new StringBuilder();
        report.append("=== 图像倾斜分析报告 ===\n");
        report.append(String.format("图像尺寸: %d x %d (%s)\n",
                size[0], size[1], isPortrait ? "竖版" : "横版"));
        report.append(String.format("检测到直线数量: %d\n", angles.size()));
        report.append(String.format("平均倾斜角度: %.2f°\n", skewAngle));

        String rotationType = (skewAngle > 0) ? "顺时针" : "逆时针";
        report.append(String.format("旋转方向: %s\n", rotationType));

        if (!angles.isEmpty()) {
            report.append("各直线角度: ");
            for (int i = 0; i < Math.min(angles.size(), 5); i++) {
                report.append(String.format("%.1f° ", angles.get(i)));
            }
            if (angles.size() > 5) {
                report.append("... (共").append(angles.size()).append("条直线)");
            }
            report.append("\n");
        }

        // 给出修正建议
        if (Math.abs(skewAngle) > 2.0) {
            report.append(String.format("建议: 需要校正 %.2f° 的倾斜\n", Math.abs(skewAngle)));
        } else {
            report.append("图像基本端正，无需校正\n");
        }

        return report.toString();
    }

    /**
     * 使用 ImageMagick 进行去黑边的完整操作
     * 输入： 经过Canny边缘检测 magick input.jpg -canny  0x1+10%+30% edges.png 的图像
     * 返回： 去除黑边的 magick 命令
     *
     * 思路： 1、将经过边缘检测后的图像进行 水平 垂直 投影；
     *       2、对于 投影 后的图像进行分析，水平 垂直 的投影图像都从两头分析，找到白色的像素点，其实就是原图的黑色边框的部分
     *       3、根据得到的四周的这个边框的信息，封装出来一个 ImageMagick 的命令，将四周的黑色边框进行白色像素填充
     * @throws Exception
     */
    @Test
    public void getBorderTest() throws Exception {
        String inputPath = "C:\\Users\\86181\\Desktop\\edges.png";
        String colPath = "C:\\Users\\86181\\Desktop\\col_projection.png";
        String rowPath = "C:\\Users\\86181\\Desktop\\row_projection.png";
        String removedPath = "C:\\Users\\86181\\Desktop\\removed.png";

        // 生成图像
        geneColProjection(inputPath, colPath, "col");
        geneColProjection(inputPath, rowPath, "row");

        //
        BufferedImage colProjImage = ImageIO.read(new File(colPath));
        BufferedImage rowProjImage = ImageIO.read(new File(rowPath));

        Integer inputPathHeight = colProjImage.getHeight();
        Integer inputPathWidth = rowProjImage.getWidth();

        // 分析列投影（检测左右边框）
        BorderInfo leftBorder = analyzeProjectionFromStart(rowProjImage, "左");
        // 0,1 是从这个点开始，高度为3的像素点，置为白色
        // magick edges.png -fill red -draw "rectangle 0,0 11,%[h]" left.jpg
        BorderInfo rightBorder = analyzeProjectionFromEnd(rowProjImage, "右");
        // borderStart 减去 width
        // magick left.jpg -fill red -draw "rectangle 581,0 %[w],%[h]" leftright.jpg

        // 分析行投影（检测上下边框）
        BorderInfo topBorder = analyzeProjectionFromStart(colProjImage, "上");
        // magick leftright.jpg -fill red -draw "rectangle 0,0 %[w],91" leftrighttop.jpg

        BorderInfo bottomBorder = analyzeProjectionFromEnd(colProjImage, "下");
        //  borderStart 减去 width
        // magick leftrighttop.jpg -fill red -draw "rectangle 0,822 592,832" leftrighttopbottom.jpg

        List<String> command = Arrays.asList(
                "magick",
                inputPath,
                "-fill", "white",
                "-draw",
                "\"",
                "rectangle", "0,", "0 ", leftBorder.width + "", ",%[h];",
                "rectangle", rightBorder.borderStart - rightBorder.width + "" , ",0 ", "%[w]", ",%[h];",
                "rectangle", "0,", "0 ", "%[w],", topBorder.width + "",
                "rectangle", "0,", bottomBorder.borderStart - bottomBorder.width + "", " ", inputPathWidth + "", ",", inputPathHeight + "",
                "\"",
                removedPath
        );
        System.out.println(String.join(" ", command));
    }

    /**
     * 生成 水平 垂直 投影 的图像
     *         // magick edges.png -threshold 50% -negate -resize 1x\! col_projection.png
     *         // magick edges.png -threshold 50% -negate -resize x1\! row_projection.png
     * @param inputPath
     * @param projectionPath
     * @param type      只能是 col 或者是 row
     * @throws Exception
     */
    public static void geneColProjection(String inputPath, String projectionPath, String type) throws Exception {

        List<String> command = Arrays.asList(
                "magick",
                inputPath,
                "-threshold", "50%",
                "-negate", "-resize",
                (type.equals("col") ? "1x\\!" : "x1\\!"),
                projectionPath
        );
        ExecutorService tempExecutor = Executors.newSingleThreadExecutor();
        try {
            ProcessResult result = ProcessPoolUtil.runCommand(command, 10, tempExecutor);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdownNow();
            }
        }
    }

    private static final int MIN_BORDER_WIDTH = 1;  // 最小边框宽度

    /**
     * 从开始端分析投影图像（左边缘或上边缘）
     */
    private static BorderInfo analyzeProjectionFromStart(BufferedImage projImage, String edgeName) {
        int width = projImage.getWidth();
        int height = projImage.getHeight();

        // 确定是水平投影还是垂直投影
        boolean isHorizontal = height == 1; // 高度为1是水平投影

        int borderWidth = 0;
        int borderStart = -1;
        boolean foundBorderEnd = false;

        // 从开始端扫描
        int scanLength = isHorizontal ? width : height;

        for (int i = 0; i < scanLength; i++) {
            int rgb = isHorizontal ?
                    projImage.getRGB(i, 0) :  // 水平投影：从左到右
                    projImage.getRGB(0, i);   // 垂直投影：从上到下
            if(!isProjectionWhite(rgb)) {
                continue;
            }

            if (!foundBorderEnd) {
                if (isProjectionWhite(rgb)) {
                    if (borderStart == -1) {
                        borderStart = i; // 记录起始点
                    }
                    borderWidth++;
                } else {
                    foundBorderEnd = true;
                    // 如果边框太窄，可能是噪声，重置计数
                    if (borderWidth < MIN_BORDER_WIDTH) {
                        borderWidth = 0;
                        foundBorderEnd = false;
                    }
                }
            }

            // 提前终止：如果已经扫描了足够长的距离且找到了边框结束点
            if (foundBorderEnd && i > MIN_BORDER_WIDTH * 3) {
                break;
            }
        }

        boolean hasBorder = borderWidth >= MIN_BORDER_WIDTH;
        return new BorderInfo(edgeName, hasBorder, borderWidth, borderStart);
    }

    /**
     * 从结束端分析投影图像（右边缘或下边缘）
     */
    private static BorderInfo analyzeProjectionFromEnd(BufferedImage projImage, String edgeName) {
        int width = projImage.getWidth();
        int height = projImage.getHeight();

        boolean isHorizontal = height == 1;
        int borderWidth = 0;
        int borderStart = -1;
        boolean foundBorderEnd = false;

        int scanLength = isHorizontal ? width : height;

        // 从结束端向前扫描
        for (int i = scanLength - 1; i >= 0; i--) {
            int rgb = isHorizontal ?
                    projImage.getRGB(i, 0) :  // 水平投影：从右到左
                    projImage.getRGB(0, i);   // 垂直投影：从下到上
            if(!isProjectionWhite(rgb)) {
                continue;
            }

            if (!foundBorderEnd) {
                if (isProjectionWhite(rgb)) {
                    if (borderStart == -1) {
                        borderStart = i; // 记录起始点
                    }
                    borderWidth++;
                } else {
                    foundBorderEnd = true;
                    if (borderWidth < MIN_BORDER_WIDTH) {
                        borderWidth = 0;
                        foundBorderEnd = false;
                    }
                }
            }

            if (foundBorderEnd && (scanLength - i) > MIN_BORDER_WIDTH * 3) {
                break;
            }
        }

        boolean hasBorder = borderWidth >= MIN_BORDER_WIDTH;
        return new BorderInfo(edgeName, hasBorder, borderWidth, borderStart);
    }

    /**
     * 针对投影图像的专用白色检测
     */
    private static boolean isProjectionWhite(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return (red + green + blue) < 100; // 调整这个阈值
    }

    static class BorderInfo {
        final String name;
        final boolean hasBorder;
        final int width;
        final int borderStart;

        public BorderInfo(String name, boolean hasBorder, int width, int borderStart) {
            this.name = name;
            this.hasBorder = hasBorder;
            this.width = width;
            this.borderStart = borderStart;
        }
    }

}
