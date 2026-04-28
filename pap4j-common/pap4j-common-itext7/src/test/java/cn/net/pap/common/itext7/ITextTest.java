package cn.net.pap.common.itext7;

import com.itextpdf.io.image.ImageType;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

public class ITextTest {

    private static final Logger log = LoggerFactory.getLogger(ITextTest.class);

    @Test
    public void extractTextTest() throws Exception {
        File file = TestResourceUtil.getFile("jpg.pdf");
        try (PdfReader reader = new PdfReader(file.getPath());PdfDocument pdfDoc = new PdfDocument(reader)) {
            PdfPage page = pdfDoc.getPage(1);
            PapTextExtractionStrategy7 strategy = new PapTextExtractionStrategy7(
                    page.getPageSize().getWidth(),
                    page.getPageSize().getHeight(),
                    page.getRotation(),
                    file.getPath().replace(".pdf", ".jpg")
            );
            PdfCanvasProcessor processor = new PdfCanvasProcessor(strategy);
            processor.processPageContent(page);
            String resultText = strategy.getResultantText();
            log.info("{}" + resultText);
        } catch (Exception e) {
            log.error("{}", e);
        }

    }

    public class PapTextExtractionStrategy7 implements IEventListener {

        private final StringBuilder withPointString = new StringBuilder();
        private final Set<String> encodingSet = new HashSet<>();
        private final List<ImageRenderInfo> allImages = new ArrayList<>();

        private int imageDPI = 300;
        private final float pageWidth;
        private final float pageHeight;
        private final int pageRotation;
        private final String jpgPath;

        public PapTextExtractionStrategy7(float pageWidth, float pageHeight, int pageRotation, String jpgPath) {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.pageRotation = pageRotation;
            this.jpgPath = jpgPath;
        }


        @Override
        public Set<EventType> getSupportedEvents() {
            return new HashSet<>(Arrays.asList(EventType.RENDER_TEXT, EventType.RENDER_IMAGE));
        }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            switch (type) {
                case RENDER_TEXT:
                    handleRenderText((TextRenderInfo) data);
                    break;
                case RENDER_IMAGE:
                    handleRenderImage((ImageRenderInfo) data);
                    break;
            }
        }

        private void handleRenderText(TextRenderInfo renderInfo) {
            List<TextRenderInfo> charInfos = renderInfo.getCharacterRenderInfos();

            for (TextRenderInfo charInfo : charInfos) {
                String text = charInfo.getText();
                Rectangle rect = charInfo.getDescentLine().getBoundingRectangle();

                if (charInfo.getFont() != null) {
                    String encoding = getFontEncoding(renderInfo);
                    if (encoding != null && !encoding.isEmpty()) {
                        encodingSet.add(encoding);
                    }
                }

                if ((text == null || text.isEmpty()) && encodingSet.contains("Cp1252")) {
                    try {
                        String tmp = new String(charInfo.getPdfString().getValueBytes(), "ISO-8859-1");
                        if (isChineseByUnicodeBlock(tmp)) {
                            text = tmp;
                        }
                    } catch (UnsupportedEncodingException ignored) {
                    }
                }

                withPointString.append(text).append("[").append(String.format("%.2f %.2f %.2f %.2f", rect.getX(), rect.getX() + rect.getWidth(), rect.getY(), rect.getY() + rect.getHeight())).append("]").append("{}<>").append("\n");
            }
        }

        private void handleRenderImage(ImageRenderInfo imageInfo) {
            allImages.add(imageInfo);

            try {
                PdfImageXObject img = imageInfo.getImage();
                if (img == null) return;

                float widthPx = img.getWidth();
                float heightPx = img.getHeight();

                float widthPt = imageInfo.getImageCtm().get(Matrix.I11);
                float heightPt = imageInfo.getImageCtm().get(Matrix.I22);

                float dpiX = widthPx / (widthPt / 72f);
                float dpiY = heightPx / (heightPt / 72f);

                int dpiXInt = Math.round(dpiX);
                int dpiYInt = Math.round(dpiY);

                if (Math.abs(dpiXInt - dpiYInt) < 3) {
                    this.imageDPI = Math.max(dpiXInt, dpiYInt);
                }

                if (dpiXInt == Integer.MAX_VALUE && dpiYInt == Integer.MAX_VALUE) {
                    this.imageDPI = 72;
                }

            } catch (Exception e) {
                log.error("{}", e);
            }
        }

        public String getResultantText() {
            // pdf 内可能存在多张图像，这里可以做一个拼接
            if(null != this.jpgPath && !"".equals(this.jpgPath)) {
                List<ImageRenderInfo> allImagesTmp = this.allImages;
                if(allImagesTmp != null && allImagesTmp.size() > 0) {
                    try {
                        BufferedImage fullImage = mergeImagesByPosition(allImages, this.pageWidth, this.pageHeight, this.imageDPI);
                        // 获取 JPEG ImageWriter
                        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                        if (!writers.hasNext()) {
                            throw new IllegalStateException("No JPEG ImageWriter found!");
                        }
                        ImageWriter writer = writers.next();
                        File file = new File(this.jpgPath);
                        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
                            writer.setOutput(ios);
                            ImageWriteParam param = writer.getDefaultWriteParam();
                            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            param.setCompressionQuality(0.6f);
                            writer.write(null, new IIOImage(fullImage, null, null), param);
                        } finally {
                            writer.dispose();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return "[" + imageDPI + "]" + withPointString.toString();
        }

        private boolean isChineseByUnicodeBlock(String text) {
            for (char c : text.toCharArray()) {
                Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
                if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                    return true;
                }
            }
            return false;
        }

        private String getFontEncoding(TextRenderInfo info) {
            try {
                if (info.getFont() != null && info.getFont().getPdfObject() != null) {
                    PdfDictionary fontDict = info.getFont().getPdfObject();
                    PdfName encoding = fontDict.getAsName(PdfName.Encoding);
                    if (encoding != null) {
                        return encoding.getValue();
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }


        public static BufferedImage mergeImagesByPosition(List<ImageRenderInfo> imageInfos, float pageWidthPt, float pageHeightPt, int dpi) throws IOException {
            float scale = dpi / 72f; // 1pt = 1/72 inch

            int canvasWidth = Math.round(pageWidthPt * scale);
            int canvasHeight = Math.round(pageHeightPt * scale);

            BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();

            // 设置白底
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, canvasWidth, canvasHeight);

            for (ImageRenderInfo imageInfo : imageInfos) {
                PdfImageXObject imageObject = imageInfo.getImage();
                if (imageObject == null) continue;

                BufferedImage img = imageObject.getBufferedImage();
                if(img == null && imageObject.identifyImageType().name().equals(ImageType.JBIG2.name())) {
                    img = extractJBIG2AsBufferedImageWithTransparency(imageInfo);
                }
                if (img == null) continue;

                Matrix ctm = imageInfo.getImageCtm();

                float xPt = ctm.get(Matrix.I31);     // X位置 (PDF左下角)
                float yPt = ctm.get(Matrix.I32);     // Y位置 (PDF左下角)
                float wPt = ctm.get(Matrix.I11);     // 宽度
                float hPt = ctm.get(Matrix.I22);     // 高度

                // PDF原点在左下，Java画布在左上 → Y轴需翻转
                int xPx = Math.round(xPt * scale);
                int yPx = Math.round((pageHeightPt - yPt - hPt) * scale); // 上翻
                int wPx = Math.round(wPt * scale);
                int hPx = Math.round(hPt * scale);

                g.drawImage(img, xPx, yPx, wPx, hPx, null);
            }

            g.dispose();
            return canvas;
        }

        public static BufferedImage extractJBIG2AsBufferedImageWithTransparency(ImageRenderInfo renderInfo) {
            try {
                PdfImageXObject imageXObj = renderInfo.getImage();
                if (imageXObj == null) return null;

                byte[] jbig2Bytes = imageXObj.getImageBytes(true);  // get filtered bytes

                PdfDictionary imgDict = imageXObj.getPdfObject();

                byte[] globalBytes = extractGlobalBytesFromDecodeParms(imgDict.getAsDictionary(PdfName.DecodeParms));

                ByteArrayOutputStream merged = new ByteArrayOutputStream();
                if (globalBytes != null) merged.write(globalBytes);
                merged.write(jbig2Bytes);

                ByteArrayInputStream bais = new ByteArrayInputStream(merged.toByteArray());
                MemoryCacheImageInputStream iis = new MemoryCacheImageInputStream(bais);

                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JBIG2");
                if (!readers.hasNext()) throw new RuntimeException("No JBIG2 ImageReader found. Make sure jbig2-imageio is in classpath.");

                ImageReader reader = readers.next();
                reader.setInput(iis);
                BufferedImage image = reader.read(0);

                // Handle /ImageMask
                PdfBoolean imageMask = imgDict.getAsBoolean(PdfName.ImageMask);
                if (imageMask != null && imageMask.getValue()) {
                    image = convertMaskToAlpha(image);
                } else {
                    PdfObject smask = imgDict.get(PdfName.SMask);
                    if (smask instanceof PdfStream) {
                        PdfStream smaskStream = (PdfStream) smask;
                        PdfImageXObject smaskXObj = new PdfImageXObject(smaskStream);
                        BufferedImage smaskImg = smaskXObj.getBufferedImage();

                        if (smaskImg != null) {
                            image = applyAlphaMask(image, smaskImg);
                        }
                    } else {
                        image = convertWhiteToTransparent(image);
                    }
                }

                return image;

            } catch (IOException e) {
                log.error("{}", e);
                return null;
            }
        }

        private static byte[] extractGlobalBytesFromDecodeParms(PdfDictionary decodeParms) throws IOException {
            if (decodeParms == null) return null;

            PdfObject globalObj = decodeParms.get(PdfName.JBIG2Globals);
            if (globalObj == null) return null;

            if (globalObj instanceof PdfStream) {
                return ((PdfStream) globalObj).getBytes();
            } else if (globalObj instanceof PdfIndirectReference) {
                PdfObject indirect = ((PdfIndirectReference) globalObj).getRefersTo();
                if (indirect instanceof PdfStream) {
                    return ((PdfStream) indirect).getBytes();
                }
            }
            return null;
        }

        private static BufferedImage convertMaskToAlpha(BufferedImage maskImg) {
            int w = maskImg.getWidth();
            int h = maskImg.getHeight();
            BufferedImage newImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = maskImg.getRGB(x, y) & 0xFFFFFF;
                    int alpha = (rgb == 0xFFFFFF) ? 0 : 255; // 白色透明
                    int argb = (alpha << 24) | 0x000000; // 黑色前景
                    newImage.setRGB(x, y, argb);
                }
            }
            return newImage;
        }

        private static BufferedImage applyAlphaMask(BufferedImage img, BufferedImage mask) {
            int w = Math.min(img.getWidth(), mask.getWidth());
            int h = Math.min(img.getHeight(), mask.getHeight());
            BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = img.getRGB(x, y);
                    int alpha = 255 - (mask.getRGB(x, y) & 0xFF);
                    alpha = Math.min(255, Math.max(0, alpha));
                    int newArgb = (alpha << 24) | (rgb & 0x00FFFFFF);
                    result.setRGB(x, y, newArgb);
                }
            }
            return result;
        }

        private static BufferedImage convertWhiteToTransparent(BufferedImage img) {
            int w = img.getWidth();
            int h = img.getHeight();
            BufferedImage newImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = img.getRGB(x, y);
                    if ((rgb & 0x00FFFFFF) == 0x00FFFFFF) {
                        newImage.setRGB(x, y, 0x00FFFFFF); // transparent
                    } else {
                        newImage.setRGB(x, y, (0xFF << 24) | (rgb & 0x00FFFFFF));
                    }
                }
            }
            return newImage;
        }

    }


}
