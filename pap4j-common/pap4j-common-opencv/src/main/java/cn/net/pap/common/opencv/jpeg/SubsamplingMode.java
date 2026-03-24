package cn.net.pap.common.opencv.jpeg;

/**
 * 色度子采样模式枚举
 */
public enum SubsamplingMode {

    YUV_444(1, 1), // 最高画质，无色度压缩

    YUV_422(2, 1), // 平衡模式

    YUV_420(2, 2); // 最高压缩率（Web 最常用）

    private final String hFactor;

    private final String vFactor;

    SubsamplingMode(int h, int v) {
        this.hFactor = String.valueOf(h);
        this.vFactor = String.valueOf(v);
    }

    public String getHFactor() {
        return hFactor;
    }

    public String getVFactor() {
        return vFactor;
    }

}
