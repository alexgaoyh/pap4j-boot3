package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
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
     * @throws Exception
     */
    // @Test
    public void test3() throws Exception {
        String s = angleInfoStrList("C:\\Users\\86181\\Desktop\\1.jpg", "C:\\Users\\86181\\Desktop\\1_canny.jpg", 300);
        Direction dir = detectTextDirection(s);
        System.out.println("文本方向: " + dir.getDesc());
    }

    public static String angleInfoStrList(String inputPath, String tmpCannyPath, Integer minLength) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "magick",
                inputPath,
                "-canny", "0x1+10%+30%",
                "+write", tmpCannyPath,
                "-background", "none",
                "-fill", "red",
                "-hough-lines", "9x9+" + minLength,
                "MVG:-"
        );
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
                return stdOutput.toString();
            }

        } catch (IOException e) {
            throw e;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly(); // 确保进程被终止
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
}
