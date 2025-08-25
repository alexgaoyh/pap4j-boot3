package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
