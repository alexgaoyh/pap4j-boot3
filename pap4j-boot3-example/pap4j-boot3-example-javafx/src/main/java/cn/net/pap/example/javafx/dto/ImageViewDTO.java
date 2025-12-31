package cn.net.pap.example.javafx.dto;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 图像对象，含 image 和 imageAbsolutePath
 */
public class ImageViewDTO implements Serializable {

    /**
     * 图像信息
     */
    private final Image image;

    /**
     * 图像绝对路径
     */
    private final String imageAbsolutePath;

    /**
     * 原始图像的宽
     */
    private final Integer sourceWidth;

    /**
     * 原始图像的高
     */
    private final Integer sourceHeight;

    /**
     * 目标展示图像的宽
     */
    private final Integer targetWidth;

    public ImageViewDTO(Image image, String imageAbsolutePath, Integer sourceWidth, Integer sourceHeight, Integer targetWidth) {
        this.image = image;
        this.imageAbsolutePath = imageAbsolutePath;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.targetWidth = targetWidth;
    }

    public Image getImage() {
        return image;
    }

    public String getImageAbsolutePath() {
        return imageAbsolutePath;
    }

    public Integer getSourceWidth() {
        return sourceWidth;
    }

    public Integer getSourceHeight() {
        return sourceHeight;
    }

    public Integer getTargetWidth() {
        return targetWidth;
    }

    /**
     * 将缩放后的矩形区域转换为原始图像中的矩形区域
     * 计算过程使用高精度小数，最终结果四舍五入取整
     *
     * @param scaledRect 缩放后的矩形区域（相对于显示图像）
     * @param dto ImageViewDTO对象，包含缩放信息
     * @return 原始图像中的矩形区域（坐标和尺寸均为整数）
     */
    public static Rectangle2D convertToOriginalRectangle(Rectangle2D scaledRect, ImageViewDTO dto) {
        if (scaledRect == null || dto == null) {
            return scaledRect;
        }

        // 使用BigDecimal进行高精度计算
        BigDecimal scaleRatio = calculateScaleRatioWithPrecision(dto);

        // 如果未缩放（scaleRatio = 1.0），直接返回原矩形（已取整）
        if (scaleRatio.compareTo(BigDecimal.ONE) == 0) {
            return roundRectangleToInteger(scaledRect);
        }

        // 将输入值转换为BigDecimal
        BigDecimal scaledMinX = BigDecimal.valueOf(scaledRect.getMinX());
        BigDecimal scaledMinY = BigDecimal.valueOf(scaledRect.getMinY());
        BigDecimal scaledWidth = BigDecimal.valueOf(scaledRect.getWidth());
        BigDecimal scaledHeight = BigDecimal.valueOf(scaledRect.getHeight());

        // 计算原始图像中的坐标和尺寸（高精度除法）
        BigDecimal originalMinX = scaledMinX.divide(scaleRatio, 20, RoundingMode.HALF_UP);
        BigDecimal originalMinY = scaledMinY.divide(scaleRatio, 20, RoundingMode.HALF_UP);
        BigDecimal originalWidth = scaledWidth.divide(scaleRatio, 20, RoundingMode.HALF_UP);
        BigDecimal originalHeight = scaledHeight.divide(scaleRatio, 20, RoundingMode.HALF_UP);

        // 四舍五入取整
        int roundedMinX = originalMinX.setScale(0, RoundingMode.HALF_UP).intValue();
        int roundedMinY = originalMinY.setScale(0, RoundingMode.HALF_UP).intValue();
        int roundedWidth = originalWidth.setScale(0, RoundingMode.HALF_UP).intValue();
        int roundedHeight = originalHeight.setScale(0, RoundingMode.HALF_UP).intValue();

        // 确保宽度和高度为正数
        roundedWidth = Math.max(0, roundedWidth);
        roundedHeight = Math.max(0, roundedHeight);

        return new Rectangle2D(
                roundedMinX,
                roundedMinY,
                roundedWidth,
                roundedHeight
        );
    }

    /**
     * 使用BigDecimal高精度计算缩放比例（保留20位小数）
     */
    private static BigDecimal calculateScaleRatioWithPrecision(ImageViewDTO dto) {
        Integer targetWidth = dto.getTargetWidth();
        Integer sourceWidth = dto.getSourceWidth();

        if (targetWidth >= sourceWidth) {
            return BigDecimal.ONE;
        }

        // 使用BigDecimal进行高精度计算
        BigDecimal target = new BigDecimal(targetWidth);
        BigDecimal source = new BigDecimal(sourceWidth);

        // 除法保留20位小数
        return target.divide(source, 20, RoundingMode.HALF_UP);
    }

    /**
     * 将矩形坐标四舍五入为整数
     */
    private static Rectangle2D roundRectangleToInteger(Rectangle2D rect) {
        if (rect == null) {
            return null;
        }

        int minX = (int) Math.round(rect.getMinX());
        int minY = (int) Math.round(rect.getMinY());
        int width = (int) Math.round(rect.getWidth());
        int height = (int) Math.round(rect.getHeight());

        // 确保宽度和高度为正数
        width = Math.max(0, width);
        height = Math.max(0, height);

        return new Rectangle2D(minX, minY, width, height);
    }

}
