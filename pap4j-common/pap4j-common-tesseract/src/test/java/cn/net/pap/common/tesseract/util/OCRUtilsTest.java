package cn.net.pap.common.tesseract.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class OCRUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(OCRUtilsTest.class);

    @Test
    public void test1() throws Exception {
        try {
            java.io.File tempFile = saveImageToFile(createTestImage("测试Tesseract OCR功能"), "ocr_test_1_");
            List<OCRUtils.OCRResult> chi = OCRUtils.recognizeWithCoordinates("d:\\tessdata", tempFile.getAbsolutePath(), "chi_sim");
            System.out.println(chi);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (OCRUtils.OCRException e) {
            log.error(e.getMessage());
        }
    }

    @Test
    public void test2() throws Exception {
        try {
            java.io.File tempFile = saveImageToFile(createTestImage("测试Tesseract OCR功能"), "ocr_test_2_");
            List<OCRUtils.OCRResult> chi = OCRUtils.recognizeWithWordCoordinates("d:\\tessdata", tempFile.getAbsolutePath(), "chi_sim");
            System.out.println(chi);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (OCRUtils.OCRException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 文字有正向，逆向等，一个不太友好的方案，把这个图像朝着各个方向翻转，看最后OCR的结果，哪个置信度高，就把哪个当做正确的方向
     * @throws Exception
     */
    @Test
    public void test3() throws Exception {
        try {
            // 生成一张包含特定汉字的初始图片 (0度)
            java.awt.image.BufferedImage image0 = createTestImage("测试Tesseract OCR功能");

            // 旋转图片生成不同方向的版本
            java.awt.image.BufferedImage image90 = rotateImage(image0, 90);
            java.awt.image.BufferedImage image180 = rotateImage(image0, 180);
            java.awt.image.BufferedImage image270 = rotateImage(image0, 270);

            // 写入不同方向的图片并创建临时文件
            java.io.File file0 = saveImageToFile(image0, "ocr_test_0_");
            java.io.File file90 = saveImageToFile(image90, "ocr_test_90_");
            java.io.File file180 = saveImageToFile(image180, "ocr_test_180_");
            java.io.File file270 = saveImageToFile(image270, "ocr_test_270_");

            // 使用生成的临时文件进行 OCR 识别
            List<OCRUtils.OCRResult> list0 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", file0.getAbsolutePath(), "chi_sim");
            List<OCRUtils.OCRResult> list90 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", file90.getAbsolutePath(), "chi_sim");
            List<OCRUtils.OCRResult> list180 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", file180.getAbsolutePath(), "chi_sim");
            List<OCRUtils.OCRResult> list270 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", file270.getAbsolutePath(), "chi_sim");

            printResult("0度", list0);
            printResult("90度", list90);
            printResult("180度", list180);
            printResult("270度", list270);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (OCRUtils.OCRException e) {
            log.error(e.getMessage());
        }
    }

    private java.awt.image.BufferedImage createTestImage(String text) {
        // 提高分辨率以模拟 300 DPI 级别的高清图像
        int width = 3200;
        int height = 600;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = image.createGraphics();
        
        // 开启抗锯齿和高质量渲染，让文字边缘更清晰平滑
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(java.awt.Color.BLACK);
        // 使用更适合中文的黑体或宋体，确保字模标准，且不带多余的修饰
        g2d.setFont(new java.awt.Font("黑体", java.awt.Font.PLAIN, 160));
        // 调整文字的起始坐标，给予四周充分的留白 (padding)，防止 Tesseract 将文字当成边缘噪点切掉
        g2d.drawString(text, 250, 350);
        g2d.dispose();
        return image;
    }

    private java.io.File saveImageToFile(java.awt.image.BufferedImage image, String prefix) throws IOException {
        java.io.File file = java.io.File.createTempFile(prefix, ".png");
        file.deleteOnExit();
        
        // 写入 PNG 时附加 300 DPI 的元数据信息，防止 Tesseract 报 Invalid resolution 警告
        java.util.Iterator<javax.imageio.ImageWriter> writers = javax.imageio.ImageIO.getImageWritersByFormatName("png");
        if (writers.hasNext()) {
            javax.imageio.ImageWriter writer = writers.next();
            javax.imageio.ImageWriteParam writeParam = writer.getDefaultWriteParam();
            javax.imageio.ImageTypeSpecifier typeSpecifier = javax.imageio.ImageTypeSpecifier.createFromBufferedImageType(image.getType());
            javax.imageio.metadata.IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);

            if (metadata != null && !metadata.isReadOnly()) {
                try {
                    String metaFormatName = "javax_imageio_png_1.0";
                    org.w3c.dom.Node root = metadata.getAsTree(metaFormatName);
                    org.w3c.dom.Element pHYs = new javax.imageio.metadata.IIOMetadataNode("pHYs");
                    // 300 DPI 约等于 11811 像素/米 (300 / 0.0254)
                    pHYs.setAttribute("pixelsPerUnitXAxis", "11811");
                    pHYs.setAttribute("pixelsPerUnitYAxis", "11811");
                    pHYs.setAttribute("unitSpecifier", "meter");
                    root.appendChild(pHYs);
                    metadata.mergeTree(metaFormatName, root);
                } catch (Exception e) {
                    log.warn("Failed to set DPI metadata", e);
                }
            }

            try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(file)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(image, null, metadata), writeParam);
            } finally {
                writer.dispose();
            }
        } else {
            javax.imageio.ImageIO.write(image, "png", file);
        }
        
        return file;
    }

    private void printResult(String angle, List<OCRUtils.OCRResult> list) {
        java.util.Optional<OCRUtils.OCRResult> pageResult = list.stream().filter(e -> "PAGE".equals(e.getLevel())).findFirst();
        if (pageResult.isPresent()) {
            OCRUtils.OCRResult res = pageResult.get();
            String text = res.getText() != null ? res.getText().replace("\n", "").replace("\r", "") : "null";
            System.out.println(angle + " -> 置信度: " + res.getConfidence() + ", 识别文本: [" + text + "]");
        } else {
            System.out.println(angle + " -> 未识别到PAGE");
        }
    }

    private java.awt.image.BufferedImage rotateImage(java.awt.image.BufferedImage originalImage, int degree) {
        int w = originalImage.getWidth();
        int h = originalImage.getHeight();
        int newW = (degree == 90 || degree == 270) ? h : w;
        int newH = (degree == 90 || degree == 270) ? w : h;
        
        java.awt.image.BufferedImage rotatedImage = new java.awt.image.BufferedImage(newW, newH, originalImage.getType());
        java.awt.Graphics2D g2d = rotatedImage.createGraphics();
        
        // 填充白色背景
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, newW, newH);
        
        g2d.translate((newW - w) / 2.0, (newH - h) / 2.0);
        g2d.rotate(Math.toRadians(degree), w / 2.0, h / 2.0);
        g2d.drawRenderedImage(originalImage, null);
        g2d.dispose();
        
        return rotatedImage;
    }

}
