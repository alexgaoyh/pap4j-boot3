package cn.net.pap.common.boofcv;

import boofcv.abst.feature.detect.line.DetectLine;
import boofcv.alg.filter.blur.GBlurImageOps;
import boofcv.factory.feature.detect.line.ConfigHoughFootSubimage;
import boofcv.factory.feature.detect.line.ConfigHoughGradient;
import boofcv.factory.feature.detect.line.FactoryDetectLine;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayU8;
import georegression.struct.line.LineParametric2D_F32;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 霍夫变换 - 图像倾斜角度
 */
public class DetectLineUtilss {

    /**
     * 使用霍夫变换，获得图像可能的倾斜角度
     *
     * @param bufferedImage
     * @param maxLines
     * @param type 1=houghLinePolar  2=houghLineFoot  3=houghLineFootSub
     * @return
     */
    public static Double getAngleByHoughLines(BufferedImage bufferedImage, int maxLines, Integer type) {
        List<Double> anglesByHoughLines = getAnglesByHoughLines(bufferedImage, maxLines, type);
        Double angle = getAngle(anglesByHoughLines);
        return angle;
    }

    /**
     * 霍夫变换 获得线的倾斜角度
     *
     * @param bufferedImage
     * @param maxLines
     * @param type 1=houghLinePolar  2=houghLineFoot  3=houghLineFootSub
     * @return
     */
    private static List<Double> getAnglesByHoughLines(BufferedImage bufferedImage, int maxLines, Integer type) {
        GrayU8 input = ConvertBufferedImage.convertFromSingle(bufferedImage, null, GrayU8.class);
        GrayU8 blurred = input.createSameShape();
        GBlurImageOps.gaussian(input, blurred, 0, 5, null);

        DetectLine<GrayU8> detectorPolar = null;
        if(type == 1) {
            detectorPolar = FactoryDetectLine.houghLinePolar(
                    new ConfigHoughGradient(maxLines), null, GrayU8.class);
        }
        if(type == 2) {
            detectorPolar = FactoryDetectLine.houghLineFoot(
                    new ConfigHoughGradient(maxLines), null, GrayU8.class);
        }
        if(type == 3) {
            detectorPolar = FactoryDetectLine.houghLineFootSub(
                    new ConfigHoughFootSubimage(3, 8, 5, 25, maxLines, 2, 2), GrayU8.class);
        }


        List<LineParametric2D_F32> lineParametric2D_f32s = detectorPolar.detect(blurred);
        List<Double> angles = calculateAngles(lineParametric2D_f32s);

        return angles;
    }

    /**
     * 将 LineParametric2D_F32 对象转换为 倾斜角度
     *
     * @param lines
     * @return
     */
    private static List<Double> calculateAngles(List<LineParametric2D_F32> lines) {
        List<Double> angles = new ArrayList<>();
        for (LineParametric2D_F32 line : lines) {
            double angle = Math.atan(line.slope.y / line.slope.x) * (180 / Math.PI);
            if (angle < 0) {
                angle = angle + 90;
            }
            angles.add(angle);
        }
        return angles;
    }

    /**
     * 取平均值
     *
     * @param angles
     * @return
     */
    private static Double getAngle(List<Double> angles) {
        Double count = 0.0;
        Integer sum = 0;
        for (Double tmp : angles) {
            if (tmp < 1 || tmp > 89) {
                continue;
            }
            count = count + tmp;
            sum = sum + 1;
        }
        return count / sum;
    }

}
