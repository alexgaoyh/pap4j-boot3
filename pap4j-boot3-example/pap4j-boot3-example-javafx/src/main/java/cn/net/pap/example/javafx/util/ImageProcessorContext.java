package cn.net.pap.example.javafx.util;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import cn.net.pap.example.javafx.dto.ExecResult;
import org.h2.util.StringUtils;

/**
 * Strategy Context
 */
public class ImageProcessorContext {

    // 静态策略实例
    private static final ImageProcessorStrategy STRATEGY;

    static {
        String imageProcessorType = ApplicationProperties.get("image.processor.type");
        if (StringUtils.isNullOrEmpty(imageProcessorType)) {
            throw new RuntimeException("image processor type is empty");
        }
        if ("imagemagick".equals(imageProcessorType)) {
            STRATEGY = new ImageProcessorStrategyImageMagick();
        } else if ("libvips".equals(imageProcessorType)) {
            STRATEGY = new ImageProcessorStrategyLibvips();
        } else {
            throw new RuntimeException("image processor type must be either imagemagick or libvips");
        }
    }

    private ImageProcessorContext() {
        throw new UnsupportedOperationException("This is a static utility class");
    }

    public static ExecResult imageRemoveIn(String inputPath, String outputPath, double x1, double y1, double x2, double y2) {
        return STRATEGY.imageRemoveIn(inputPath, outputPath, x1, y1, x2, y2);
    }

}
