package cn.net.pap.common.opencv.jpeg;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * jpeg subsampling
 */
public class JpegSubsamplingUtil {

    /**
     * 核心方法：动态控制 JPEG 写入的质量与子采样
     *
     * @param image   原始图像
     * @param output  输出的 Path 路径
     * @param quality 压缩质量 (0.0f - 1.0f)
     * @param use420  true 表示使用 2x2 子采样 (4:2:0)，false 表示全采样 (4:4:4)
     */
    public static void writeJpegWithSubsampling(BufferedImage image, Path output, float quality, boolean use420) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("未找到 JPEG 编码器");
        }
        ImageWriter writer = writers.next();

        // Path 转 File 供 ImageOutputStream 使用
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(ios);

            // 1. 设置基本的压缩质量
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            // 2. 获取默认的 Metadata，并将其转换为 XML DOM 树
            ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromRenderedImage(image);
            IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, param);
            String metadataFormat = "javax_imageio_jpeg_image_1.0";
            Element tree = (Element) metadata.getAsTree(metadataFormat);

            // 3. 拦截并修改 DOM 树中的 SOF (Start of Frame) 节点
            NodeList sofNodes = tree.getElementsByTagName("sof");
            if (sofNodes.getLength() > 0) {
                Element sof = (Element) sofNodes.item(0);
                NodeList components = sof.getElementsByTagName("componentSpec");

                if (components.getLength() >= 3) {
                    // 修改 Y (亮度) 通道的采样率
                    Element luma = (Element) components.item(0);
                    luma.setAttribute("HsamplingFactor", use420 ? "2" : "1");
                    luma.setAttribute("VsamplingFactor", use420 ? "2" : "1");

                    // 确保 Cb 和 Cr 通道的采样率始终为 1
                    for (int i = 1; i <= 2; i++) {
                        Element chroma = (Element) components.item(i);
                        chroma.setAttribute("HsamplingFactor", "1");
                        chroma.setAttribute("VsamplingFactor", "1");
                    }
                }
            }

            // 4. 将修改后的 DOM 树应用回 Metadata
            metadata.setFromTree(metadataFormat, tree);

            // 5. 写入图像
            writer.write(null, new IIOImage(image, null, metadata), param);
        } finally {
            writer.dispose();
        }
    }

    public static BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, width / 2, height);
        g2d.setColor(Color.BLUE);
        g2d.fillRect(width / 2, 0, width / 2, height);
        g2d.dispose();
        return image;
    }

}
