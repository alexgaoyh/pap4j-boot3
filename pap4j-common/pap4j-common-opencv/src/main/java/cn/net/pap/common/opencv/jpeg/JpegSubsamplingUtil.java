package cn.net.pap.common.opencv.jpeg;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGQTable;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
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
     * @param mode    子采样模式 (例如 SubsamplingMode.YUV_420)
     */
    public static void writeJpegWithSubsampling(BufferedImage image, Path output, float quality, SubsamplingMode mode) throws IOException {
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
                    luma.setAttribute("HsamplingFactor", mode.getHFactor());
                    luma.setAttribute("VsamplingFactor", mode.getVFactor());

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

    /**
     * 解析 JPEG 图像，获取其色度子采样模式 (例如 4:4:4 或 4:2:0)
     *
     * @param jpegFile 输入的 JPEG 文件
     * @return SubsamplingMode 子采样枚举，解析失败或非标返回 UNKNOWN
     */
    public static SubsamplingMode readSubsamplingMode(File jpegFile) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(jpegFile)) {
            if (iis == null) {
                throw new IOException("无法创建输入流，文件可能不存在。");
            }

            // 获取适用的解码器
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("未找到适用于该文件的 ImageReader (可能不是 JPEG)。");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true); // 优化：跳过读取图像像素，只读元数据

                // 获取第一帧的元数据
                IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata == null) {
                    return SubsamplingMode.UNKNOWN;
                }

                String metadataFormat = "javax_imageio_jpeg_image_1.0";
                Element tree = (Element) metadata.getAsTree(metadataFormat);

                // 寻找 SOF (Start of Frame) 节点
                NodeList sofNodes = tree.getElementsByTagName("sof");
                if (sofNodes.getLength() > 0) {
                    Element sof = (Element) sofNodes.item(0);
                    // 获取颜色通道组件 (通常 component 0 是 Y, 1 是 Cb, 2 是 Cr)
                    NodeList components = sof.getElementsByTagName("componentSpec");

                    if (components.getLength() > 0) {
                        // 提取 Y (亮度) 通道的采样倍率
                        Element luma = (Element) components.item(0);
                        String hFactor = luma.getAttribute("HsamplingFactor");
                        String vFactor = luma.getAttribute("VsamplingFactor");

                        // 遍历枚举进行精准匹配
                        for (SubsamplingMode mode : SubsamplingMode.values()) {
                            if (mode.getHFactor().equals(hFactor) && mode.getVFactor().equals(vFactor)) {
                                return mode;
                            }
                        }
                    }
                }
            } finally {
                reader.dispose(); // 务必释放资源
            }
        }

        return SubsamplingMode.UNKNOWN;
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

    // 标准的 IJG 亮度量化基准表（Quality = 50）
    private static final int[] STANDARD_LUMINANCE_TABLE = {16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99};

    // 预计算的特征字典库：存储 Quality 1-100 对应的所有标准亮度量化表
    private static final int[][] QUALITY_DICTIONARY = new int[101][64];

    static {
        // 类加载时预先计算 1 到 100 的所有标准量化表
        for (int q = 1; q <= 100; q++) {
            int scaleFactor;
            if (q < 50) {
                scaleFactor = 5000 / q;
            } else {
                scaleFactor = 200 - (q * 2);
            }

            for (int i = 0; i < 64; i++) {
                // 标准 IJG 缩放并取整公式
                int val = (STANDARD_LUMINANCE_TABLE[i] * scaleFactor + 50) / 100;
                // 限制在 8-bit 基准的 1-255 范围内
                if (val < 1) val = 1;
                if (val > 255) val = 255;

                QUALITY_DICTIONARY[q][i] = val;
            }
        }
    }

    /**
     * 获取 JPEG 图像的准确保存质量 (返回 1-100)
     */
    public static int getExactJpegQuality(File jpegFile) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(jpegFile)) {
            if (iis == null) throw new IOException("无法创建输入流");

            var readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new IOException("未找到适用于该文件的 ImageReader");

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata == null) return -1;

                Node root = metadata.getAsTree("javax_imageio_jpeg_image_1.0");
                int[] qTable = extractLuminanceDQT(root);

                if (qTable != null) {
                    // 1. 尝试精确字典匹配
                    int exactQuality = findExactMatch(qTable);
                    if (exactQuality != -1) {
                        return exactQuality;
                    }
                    // 2. 如果字典查不到（说明是非标自定义量化表），降级使用估算法
                    return fallbackEstimateQuality(qTable);
                }
            } finally {
                reader.dispose();
            }
        }
        return -1;
    }

    /**
     * 在特征字典库中进行 100% 原样匹配
     */
    private static int findExactMatch(int[] qTable) {
        // 从 100 往下倒序查，通常高质量图片居多，匹配更快
        for (int q = 100; q >= 1; q--) {
            if (Arrays.equals(qTable, QUALITY_DICTIONARY[q])) {
                return q;
            }
        }
        return -1; // 查无此表
    }

    /**
     * 降级估算法：应对 Photoshop "存储为 Web 所用格式" 等使用非标自定义表的特殊图片
     */
    private static int fallbackEstimateQuality(int[] qTable) {
        double totalQuality = 0;
        int validCount = 0;

        for (int i = 0; i < 64; i++) {
            int base = STANDARD_LUMINANCE_TABLE[i];
            int actual = qTable[i];
            if (actual == 1 || actual == 255) continue;

            double multiplier = (actual * 100.0 - 50.0) / base;
            double q = (multiplier < 100) ? (200.0 - multiplier) / 2.0 : 5000.0 / multiplier;

            totalQuality += q;
            validCount++;
        }

        if (validCount == 0) return qTable[0] == 1 ? 100 : 1;
        return Math.max(1, Math.min(100, (int) Math.round(totalQuality / validCount)));
    }

    /**
     * 提取亮度 DQT (修正版)
     */
    private static int[] extractLuminanceDQT(Node node) {
        if ("dqtable".equals(node.getNodeName())) {
            NamedNodeMap attributes = node.getAttributes();
            Node qtableIdNode = attributes.getNamedItem("qtableId");

            if (qtableIdNode != null && "0".equals(qtableIdNode.getNodeValue())) {
                if (node instanceof IIOMetadataNode) {
                    Object userObject = ((IIOMetadataNode) node).getUserObject();
                    if (userObject instanceof JPEGQTable) {
                        return ((JPEGQTable) userObject).getTable();
                    }
                }
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            int[] result = extractLuminanceDQT(children.item(i));
            if (result != null) return result;
        }
        return null;
    }


}
