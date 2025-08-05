package cn.net.pap.common.opencv.enums;

import java.awt.*;

/**
 * 纸张尺寸
 */
public enum PaperSize {

    A1("A1", 594, 841),  // A1: 594 × 841 mm
    A2("A2", 420, 594),  // A2: 420 × 594 mm
    A3("A3", 297, 420),  // A3: 297 × 420 mm
    A4("A4", 210, 297);  // A4: 210 × 297 mm

    private final String name;

    private final double widthMM;

    private final double heightMM;

    PaperSize(String name, double widthMM, double heightMM) {
        this.name = name;
        this.widthMM = widthMM;
        this.heightMM = heightMM;
    }

    public String getName() {
        return name;
    }

    public double getWidthMM() {
        return widthMM;
    }

    public double getHeightMM() {
        return heightMM;
    }


    // 毫米转英寸
    public static double mmToInch(double mm) {
        return mm / 25.4;
    }

    /**
     * 根据DPI和纸张尺寸计算像素尺寸
     * @param dpi
     * @param paperSize
     * @param landscape 是否横向
     * @return
     */
    public static Dimension calculatePixelSize(int dpi, PaperSize paperSize, boolean landscape) {
        double widthMM = paperSize.getWidthMM();
        double heightMM = paperSize.getHeightMM();

        if (landscape) {
            // 横向模式交换宽高
            double temp = widthMM;
            widthMM = heightMM;
            heightMM = temp;
        }

        double widthInch = mmToInch(widthMM);
        double heightInch = mmToInch(heightMM);

        int widthPx = (int) Math.round(widthInch * dpi);
        int heightPx = (int) Math.round(heightInch * dpi);

        return new Dimension(widthPx, heightPx);
    }


}
