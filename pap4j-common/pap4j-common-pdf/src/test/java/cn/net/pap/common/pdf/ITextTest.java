package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.enums.ChineseFont;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.*;
import com.itextpdf.text.pdf.parser.Vector;
import org.junit.jupiter.api.Test;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

public class ITextTest {

    /**
     * dpi 转换  PDF是72， IMAGE是 真是获取的
     */
    private static BigDecimal dpi72ToReal = new BigDecimal(300).divide(new BigDecimal(72), 2, BigDecimal.ROUND_HALF_UP);

    // @Test
    public void dirConvertTest() throws Exception {
        File[] files = new File("C:\\Users\\86181\\Desktop\\单").listFiles();

        for (File file : files) {
            if (file.isDirectory()) {
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".pdf")) {
                    PdfReader reader = new PdfReader(file.getAbsolutePath());
                    Integer pageNum = 1;
                    Rectangle pageSize = reader.getPageSize(pageNum);
                    String textWithPoints = SafePdfTextExtractor.extractTextFromPage(reader, pageNum, pageSize.getWidth(), pageSize.getHeight(), reader.getPageRotation(pageNum), file.getPath().replace(".pdf", ".jpg"));
                    System.out.println(textWithPoints);
                    System.out.println(fileName);
                }
            }
        }
    }

    @Test
    public void pointTextTest() {
        try {
            LinkedHashSet<PointTextDTO> pointTextDTOS = new LinkedHashSet<>();

            List<Map<String, Object>> centerXTextList = new ArrayList<>();

            BigDecimal minWidth = new BigDecimal(Integer.MAX_VALUE);
            BigDecimal maxWidth = new BigDecimal(Integer.MIN_VALUE);

            File file = new File("C:\\Users\\86181\\Desktop\\0029A.pdf");
            PdfReader reader = new PdfReader(file.getAbsolutePath());
            Integer pageNum = 1;
            Rectangle pageSize = reader.getPageSize(pageNum);
            String textWithPoints = SafePdfTextExtractor.extractTextFromPage(reader, pageNum, pageSize.getWidth(), pageSize.getHeight(), reader.getPageRotation(pageNum), null);

            String dpi = textWithPoints.substring(textWithPoints.indexOf("[") + 1, textWithPoints.indexOf("]"));
            textWithPoints = textWithPoints.substring(textWithPoints.indexOf("]") + 1);

            dpi72ToReal = new BigDecimal(dpi).divide(new BigDecimal(72), 2, BigDecimal.ROUND_HALF_UP);

            if(textWithPoints != null && !"".equals(textWithPoints)) {
                for (String textWithPoint : textWithPoints.split("\n")) {
                    String text = textWithPoint.substring(0, textWithPoint.indexOf("["));
                    if(text != null && !"".equals(text) && !"".equals(text.trim())) {
                        String point = textWithPoint.substring(textWithPoint.indexOf("[") + 1, textWithPoint.indexOf("]"));
                        PointTextDTO pointTextDTO = new PointTextDTO(string2Box(point, dpi72ToReal), text);
                        boolean add = pointTextDTOS.add(pointTextDTO);

                        if(add) {
                            Map<String, Object> tmp = new LinkedHashMap<>();
                            tmp.put("x", centerX(point, dpi72ToReal));
                            Map<String, Object> info = new LinkedHashMap<>();
                            info.put("point", string2Box(point, dpi72ToReal));
                            info.put("text", text);
                            tmp.put("info", info);
                            if(minWidth.compareTo(new BigDecimal(centerWidth(point, dpi72ToReal))) > 0) {
                                minWidth = new BigDecimal(centerWidth(point, dpi72ToReal)).setScale(2 , BigDecimal.ROUND_HALF_UP);
                            }
                            if(maxWidth.compareTo(new BigDecimal(centerWidth(point, dpi72ToReal))) < 0) {
                                maxWidth = new BigDecimal(centerWidth(point, dpi72ToReal)).setScale(2 , BigDecimal.ROUND_HALF_UP);
                            }
                            centerXTextList.add(tmp);
                        }
                    }

                }
            }

            System.out.println(pointTextDTOS.size());
            System.out.println(dpi);
            ObjectMapper objectMapper = new ObjectMapper();
            System.out.println(objectMapper.writeValueAsString(pointTextDTOS));
            System.out.println("-------------------------------");
            System.out.println(minWidth);
            System.out.println(maxWidth);
            System.out.println(objectMapper.writeValueAsString(centerXTextList));

            // saveRotation90Chcek(reader.getPageRotation(pageNum), pageSize, pointTextDTOS, dpi72ToReal);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class PapTextExtractionStrategy implements TextExtractionStrategy {

        private StringBuilder withPointString = new StringBuilder();

        private Integer imageDPI = 300;

        private float pageWidth = 0.0f;

        private float pageHeight = 0.0f;

        /**
         * 方向 - 不同 PDF 设置的方向不同，所以可能对应的一系列宽高和坐标就不同
         * 0:正常 ; 90:顺时针旋转 ; -90:逆时针旋转
         */
        private Integer pageRotation;

        /**
         * 记录使用的字符编码
         */
        private Set<String> encodingSet = new HashSet<>();

        private List<ImageRenderInfo> allImages = new ArrayList<>();

        private String jpgPath;

        PapTextExtractionStrategy(float pageWidth, float pageHeight, Integer pageRotation, String jpgPath) {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.pageRotation = pageRotation;
            this.jpgPath = jpgPath;
        }

        @Override
        public void beginTextBlock() {

        }

        @Override
        public void renderText(TextRenderInfo renderInfo) {
            LineSegment baselineOuter = renderInfo.getBaseline();
            if(renderInfo.getFont() != null
                    && renderInfo.getFont().getEncoding() != null
                    && !"".equals(renderInfo.getFont().getEncoding())) {
                encodingSet.add(renderInfo.getFont().getEncoding());
            }
            List<TextRenderInfo> textRenderInfos = renderInfo.getCharacterRenderInfos();
            for (int idx = 0; idx < textRenderInfos.size(); idx++) {
                TextRenderInfo info = textRenderInfos.get(idx);
                GraphicsState graphicsState = getGraphicsState(info);
                float singleSpaceWidth = graphicsState.getCharacterSpacing();
                LineSegment ascentLine = info.getAscentLine();
                LineSegment baselineInner = info.getBaseline();
                float height = getHeightByRotation(pageRotation, ascentLine, baselineOuter, baselineInner);
                Rectangle2D rect = info.getDescentLine().getBoundingRectange();
                String text = info.getText();
                if(null == text || "".equals(text)) {
                    try {
                        if(encodingSet != null && encodingSet.size() > 0 && encodingSet.contains("Cp1252")) {
                            String tmp = new String(info.getPdfString().toString().getBytes("ISO-8859-1"), "GBK");
                            if(isChineseByUnicodeBlock(tmp)) {
                                text = tmp;
                            }
                        }
                    } catch (UnsupportedEncodingException e) {
                    }
                }
                withPointString.append(text)
                        .append("[")
                        // x x' y y'
                        .append(getCoorsByRotation(pageRotation, rect, pageWidth, pageHeight, height, baselineOuter, baselineInner, idx, singleSpaceWidth, graphicsState.getFontSize()))
                        .append("]")
                        .append("{").append("").append("}")
                        .append("<").append("").append(",").append("").append(">")
                        .append("\n");
            }
            textRenderInfos.addAll(textRenderInfos);
        }

        @Override
        public void endTextBlock() {

        }

        @Override
        public void renderImage(ImageRenderInfo imageRenderInfo) {
            try {
                PdfImageObject image = imageRenderInfo.getImage();
                if (image == null) return;

                allImages.add(imageRenderInfo);

                float widthPx = Float.parseFloat(imageRenderInfo.getImage().getDictionary().get(PdfName.WIDTH).toString());
                float heightPx = Float.parseFloat(imageRenderInfo.getImage().getDictionary().get(PdfName.HEIGHT).toString());

                float widthPt = imageRenderInfo.getImageCTM().get(Matrix.I11);
                float heightPt = imageRenderInfo.getImageCTM().get(Matrix.I22);

                float widthInches = widthPt / 72;
                float heightInches = heightPt / 72;

                float dpiX = widthPx / widthInches;
                float dpiY = heightPx / heightInches;

                Integer dpiXInt = Math.round(dpiX);
                Integer dpiYInt = Math.round(dpiY);

                if (dpiXInt.equals(dpiYInt) || Math.abs(dpiXInt - dpiYInt) < 3) {
                    this.imageDPI = Math.max(dpiXInt, dpiYInt);
                }
                // 给一个默认的 72
                if(dpiXInt.equals(dpiYInt) && dpiXInt == Integer.MAX_VALUE && dpiYInt == Integer.MAX_VALUE) {
                    this.imageDPI = 72;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
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
    }

    /**
     * iText5 内部 处理结构化标记内容（Marked Content）时栈未匹配
     */
    public static class SafePdfTextExtractor {

        public static String extractTextFromPage(PdfReader reader, int pageNum, float pageWidth, float pageHeight, Integer pageRotation, String jpgPath) throws IOException {
            PapTextExtractionStrategy strategy = new PapTextExtractionStrategy(pageWidth, pageHeight, pageRotation, jpgPath);
            SafeProcessor processor = new SafeProcessor(strategy);
            PdfDictionary pageDic = reader.getPageN(pageNum);
            PdfDictionary resourcesDic = pageDic.getAsDict(PdfName.RESOURCES);

            PdfContentByte cb = new PdfContentByte(null);
            byte[] contentBytes = reader.getPageContent(pageNum);

            processor.processContent(contentBytes, resourcesDic);
            return strategy.getResultantText();
        }

        static class SafeProcessor extends PdfContentStreamProcessor {

            public SafeProcessor(RenderListener renderListener) {
                super(renderListener);

                this.registerContentOperator("EMC", new SafeEndMarkedContentOperator());

                this.registerContentOperator("BMC", new SafeBeginMarkedContentOperator());
            }

            private static Stack<?> getMarkedContentStack(PdfContentStreamProcessor processor) throws Exception {
                Field field = PdfContentStreamProcessor.class.getDeclaredField("markedContentStack");
                field.setAccessible(true);
                return (Stack<?>) field.get(processor);
            }

            static class SafeEndMarkedContentOperator implements ContentOperator {
                @Override
                public void invoke(PdfContentStreamProcessor processor, PdfLiteral operator, ArrayList<PdfObject> operands) {
                    try {
                        Stack<?> stack = SafeProcessor.getMarkedContentStack(processor);
                        if (!stack.isEmpty()) {
                            stack.pop();
                        } else {
                            System.err.println("Skipped unmatched EMC (stack was empty)");
                        }
                    } catch (Exception e) {
                        System.err.println("EMC Error: " + e.getMessage());
                    }
                }
            }

            static class SafeBeginMarkedContentOperator implements ContentOperator {
                @Override
                public void invoke(PdfContentStreamProcessor processor, PdfLiteral operator, ArrayList<PdfObject> operands) {
                    try {
                        Stack<Object> stack = (Stack<Object>) SafeProcessor.getMarkedContentStack(processor);
                        stack.push(new Object());
                    } catch (Exception e) {
                        System.err.println("BMC Error: " + e.getMessage());
                    }
                }
            }
        }

    }

    private static List<Double> string2Box(String point, BigDecimal dpi72ToReal) {
        List<Double> box = new ArrayList<>();
        for (String coor : point.split(",")) {
            box.add(new BigDecimal(coor).multiply(dpi72ToReal).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue());
        }
        return box;
    }

    private static Double centerX(String point, BigDecimal dpi72ToReal) {
        BigDecimal sum = BigDecimal.ZERO;
        String[] split = point.split(",");
        sum = sum.add(new BigDecimal(split[0]).multiply(dpi72ToReal));
        sum = sum.add(new BigDecimal(split[1]).multiply(dpi72ToReal));
        return sum.divide(new BigDecimal(2), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private static Double centerWidth(String point, BigDecimal dpi72ToReal) {
        String[] split = point.split(",");
        return new BigDecimal(split[1]).multiply(dpi72ToReal).subtract(new BigDecimal(split[0]).multiply(dpi72ToReal)).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static boolean isChineseByUnicodeBlock(String str) {
        for (char c : str.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据 pdf 是否旋转，获得 文本 的高度
     * 原方向和顺时针90度旋转后的高度取值不同
     * @param pageRotation
     * @param ascentLine
     * @param baselineOuter
     * @param baselineInner
     * @return
     */
    public static float getHeightByRotation(Integer pageRotation, LineSegment ascentLine, LineSegment baselineOuter, LineSegment baselineInner) {
        if(pageRotation == 0) {
            return baselineInner.getStartPoint().get(Vector.I2) - baselineInner.getStartPoint().get(Vector.I2);
        }
        if(pageRotation == 90) {
            return baselineInner.getEndPoint().get(Vector.I2) - baselineInner.getStartPoint().get(Vector.I2);
        }
        throw new RuntimeException("未匹配到合适的旋转方向,请联系开发人员!");
    }

    /**
     * 根据 pdf 是否旋转，获得 文本 的坐标
     * 原方向和顺时针90度旋转后的坐标不同
     * @param pageRotation
     * @param rect
     * @param pageWidth
     * @param pageHeight
     * @param widthOrHeight
     * @return
     */
    public static String getCoorsByRotation(Integer pageRotation, Rectangle2D rect, float pageWidth, float pageHeight, float widthOrHeight, LineSegment baselineOuter, LineSegment baselineInner, Integer idx, float singleSpaceWidth, float fontSize) {
        if(pageRotation == 0) {
            return new StringBuilder(rect.getX() + "").append(",").append(rect.getX() + rect.getWidth()).append(",").append(pageHeight - rect.getY()).append(",").append(pageHeight - rect.getY() + widthOrHeight).toString();
        }
        if(pageRotation == 90) {
            double rectHeight = rect.getHeight();
            if(rectHeight == 0.0d) {
                rectHeight = Math.round(Math.abs(widthOrHeight));
            }
            if(rectHeight == 0.0d) {
                rectHeight = fontSize;
            }
            Vector startOuter = baselineOuter.getStartPoint();
            float adjustedX = startOuter.get(1);
            float adjustedY = pageWidth - baselineOuter.getStartPoint().get(0) - Float.parseFloat(Math.round(rectHeight) + Math.abs(singleSpaceWidth) + "") * (idx + 1);

            return new StringBuilder(adjustedX - Math.round(rectHeight / 2) + "").append(",").append(adjustedX + Math.round(rectHeight / 2) + "").append(",").append(adjustedY + "").append(",").append(adjustedY + "").toString();
        }
        throw new RuntimeException("未匹配到合适的旋转方向,请联系开发人员!");
    }

    /**
     * 反射获得 GraphicsState
     * @param renderInfo
     * @return
     */
    public static GraphicsState getGraphicsState(TextRenderInfo renderInfo) {
        try {
            Field graphicsStateField = TextRenderInfo.class.getDeclaredField("gs");
            graphicsStateField.setAccessible(true);
            return (GraphicsState) graphicsStateField.get(renderInfo);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
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
            PdfImageObject imageObject = imageInfo.getImage();
            if (imageObject == null) continue;

            BufferedImage img = imageObject.getBufferedImage();
            if(img == null && imageObject.getFileType().equals("jbig2")) {
                img = extractJBIG2AsBufferedImageWithTransparency(imageInfo);
            }
            if (img == null) continue;

            Matrix ctm = imageInfo.getImageCTM();

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

    /**
     * jbig2 格式图像的处理，同时处理了透明度
     * @param renderInfo
     * @return
     */
    public static BufferedImage extractJBIG2AsBufferedImageWithTransparency(ImageRenderInfo renderInfo) {
        try {
            PdfImageObject image = renderInfo.getImage();
            if (image == null) return null;

            byte[] jbig2Bytes = image.getImageAsBytes();
            if (jbig2Bytes == null) return null;

            PdfDictionary imgDict = image.getDictionary();

            // 取 JBIG2Globals，逻辑同前面（略，复用之前代码）
            byte[] globalBytes = extractGlobalBytesFromDecodeParms(imgDict.getAsDict(PdfName.DECODEPARMS));

            ByteArrayOutputStream merged = new ByteArrayOutputStream();
            if (globalBytes != null) merged.write(globalBytes);
            merged.write(jbig2Bytes);

            ByteArrayInputStream bais = new ByteArrayInputStream(merged.toByteArray());
            MemoryCacheImageInputStream iis = new MemoryCacheImageInputStream(bais);

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JBIG2");
            if (!readers.hasNext()) throw new RuntimeException("No JBIG2 reader found");

            ImageReader readerJBIG2 = readers.next();
            readerJBIG2.setInput(iis);

            BufferedImage img = readerJBIG2.read(0);

            // 检查并应用透明掩码
            PdfBoolean imageMask = imgDict.getAsBoolean(PdfName.IMAGEMASK);
            if (imageMask != null && imageMask.booleanValue()) {
                // ImageMask = true，做遮罩处理
                img = convertMaskToAlpha(img);
            } else {
                PdfObject smask = imgDict.get(PdfName.SMASK);
                if (smask != null) {
                    PdfImageObject smaskImage = new PdfImageObject((PRStream) PdfReader.getPdfObject(smask));
                    BufferedImage maskImg = smaskImage.getBufferedImage();

                    if (maskImg != null) {
                        // 将mask作为alpha通道合成
                        img = applyAlphaMask(img, maskImg);
                    }
                } else {
                    // 也可以根据需要做简单的颜色透明处理，比如把白色当透明
                    img = convertWhiteToTransparent(img);
                }
            }

            return img;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static BufferedImage convertMaskToAlpha(BufferedImage maskImg) {
        int w = maskImg.getWidth();
        int h = maskImg.getHeight();
        BufferedImage newImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = maskImg.getRGB(x, y) & 0xFFFFFF;
                int alpha = (rgb == 0xFFFFFF) ? 0 : 255; // 白色透明，黑色不透明
                int argb = (alpha << 24) | 0x000000;     // 黑色前景
                newImage.setRGB(x, y, argb);
            }
        }
        return newImage;
    }

    private static byte[] extractGlobalBytesFromDecodeParms(PdfDictionary decodeParms) throws IOException {
        PdfObject globalObj = decodeParms.get(PdfName.JBIG2GLOBALS);
        if (globalObj == null) return null;

        if (globalObj instanceof PRStream) {
            return PdfReader.getStreamBytes((PRStream) globalObj);
        } else if (globalObj instanceof PdfIndirectReference) {
            PdfObject indirectGlobal = PdfReader.getPdfObject((PdfIndirectReference) globalObj);
            if (indirectGlobal instanceof PRStream) {
                return PdfReader.getStreamBytes((PRStream) indirectGlobal);
            }
        }
        return null;
    }

    private static BufferedImage applyAlphaMask(BufferedImage img, BufferedImage mask) {
        int w = Math.min(img.getWidth(), mask.getWidth());
        int h = Math.min(img.getHeight(), mask.getHeight());

        BufferedImage newImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int alpha = (mask.getRGB(x, y) & 0xFF); // 用mask的蓝色通道作为alpha值

                // 把 alpha 赋给颜色（扩大到0~255）
                alpha = 255 - alpha; // 根据mask的定义调整反转逻辑，具体PDF看实际
                alpha = Math.min(255, Math.max(0, alpha));

                int newArgb = (alpha << 24) | (rgb & 0x00FFFFFF);
                newImage.setRGB(x, y, newArgb);
            }
        }
        return newImage;
    }

    private static BufferedImage convertWhiteToTransparent(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage newImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                // 判定白色（或接近白色）
                if ((rgb & 0x00FFFFFF) == 0x00FFFFFF) {
                    // 透明
                    newImage.setRGB(x, y, 0x00FFFFFF & 0x00FFFFFF);
                } else {
                    // 不透明，alpha 255
                    newImage.setRGB(x, y, (0xFF << 24) | (rgb & 0x00FFFFFF));
                }
            }
        }
        return newImage;
    }

    /**
     * 保存验证
     * @param pageSize
     * @param pointTextDTOS
     */
    public static void saveRotation90Chcek(Integer pageRotation, Rectangle pageSize,
                                           LinkedHashSet<PointTextDTO> pointTextDTOS, BigDecimal dpi72ToReal) {
        Document document = null;
        PdfWriter writer = null;

        try {
            // 计算页面尺寸
            float pageWidth, pageHeight;
            if(pageRotation == 90) {
                pageWidth = pageSize.getHeight() * dpi72ToReal.floatValue();
                pageHeight = pageSize.getWidth() * dpi72ToReal.floatValue();
            } else {
                pageWidth = pageSize.getWidth() * dpi72ToReal.floatValue();
                pageHeight = pageSize.getHeight() * dpi72ToReal.floatValue();
            }

            // 创建文档
            document = new Document(new Rectangle(pageWidth, pageHeight));
            writer = PdfWriter.getInstance(document, new FileOutputStream("C:\\Users\\86181\\Desktop\\123456.pdf"));
            document.open();

            PdfContentByte canvas = writer.getDirectContent();
            Map<String, BaseFont> fonts = new HashMap<>();

            // 加载字体
            for(ChineseFont chineseFont : ChineseFont.values()) {
                BaseFont font = BaseFont.createFont(
                        ChineseFont.getLocation(chineseFont.getFontName()),
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED
                );
                fonts.put(chineseFont.getFontName(), font);
            }

            // 添加文本内容
            for(PointTextDTO pointTextDTO : pointTextDTOS) {
                for(Map.Entry<String, BaseFont> entry : fonts.entrySet()) {
                    try {
                        BaseFont font = entry.getValue();
                        String text = pointTextDTO.getText();

                        // 计算字体大小
                        float fontSize;
                        if(pageRotation == 90) {
                            fontSize = (Float.parseFloat(pointTextDTO.getBox().get(1) + "") -
                                    Float.parseFloat(pointTextDTO.getBox().get(0) + "")) * dpi72ToReal.floatValue();
                        } else {
                            float boxWidth = Float.parseFloat(pointTextDTO.getBox().get(1) - pointTextDTO.getBox().get(0) + "");
                            float boxHeight = Float.parseFloat(pointTextDTO.getBox().get(3) - pointTextDTO.getBox().get(2) + "");
                            fontSize = computeFittedFontSize(font, text, boxWidth, boxHeight, 12f);
                        }

                        // 计算位置
                        float x = Float.parseFloat(pointTextDTO.getBox().get(0) + "");
                        float y = (pageRotation == 0) ?
                                (pageHeight - Float.parseFloat(pointTextDTO.getBox().get(2) + "")) :
                                Float.parseFloat(pointTextDTO.getBox().get(2) + "");

                        // 绘制文本 - 确保每个beginText()都有对应的endText()
                        canvas.beginText();
                        canvas.setFontAndSize(font, fontSize);
                        canvas.setColorFill(BaseColor.BLACK);
                        canvas.setTextMatrix(x, y);
                        canvas.showText(text);
                        canvas.endText();  // 确保每个beginText()都有对应的endText()

                        break; // 找到可用字体后跳出循环
                    } catch (Exception e) {
                        e.printStackTrace();
                        // 忽略错误继续尝试下一个字体
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(writer != null) {
                writer.close();
            }
            if(document != null) {
                document.close();  // 这会自动处理任何未关闭的文本操作
            }
        }
    }

    /**
     * 字体大小
     * @param font
     * @param text
     * @param boxWidth
     * @param boxHeight
     * @param baseFontSize
     * @return
     */
    public static float computeFittedFontSize(BaseFont font, String text,
                                              float boxWidth, float boxHeight, float baseFontSize) {
        try {
            float textWidth = font.getWidthPoint(text, baseFontSize);
            float textHeight = font.getFontDescriptor(BaseFont.BBOXURY, baseFontSize) -
                    font.getFontDescriptor(BaseFont.BBOXLLY, baseFontSize);

            float scaleFactor = Math.min(boxWidth / textWidth, boxHeight / textHeight);
            if(scaleFactor == 0.0f) {
                scaleFactor = Math.max(boxWidth / textWidth, boxHeight / textHeight);
            }
            return baseFontSize * scaleFactor;
        } catch (Exception e) {
            e.printStackTrace();
            return baseFontSize;
        }
    }


    class PointTextDTO implements Serializable {

        /**
         * 单字坐标区域
         */
        private List<Double> box;

        /**
         * 单字文本
         */
        private String text;

        public PointTextDTO(List<Double> box, String text) {
            this.box = box;
            this.text = text;
        }

        public List<Double> getBox() {
            return box;
        }

        public void setBox(List<Double> box) {
            this.box = box;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PointTextDTO that = (PointTextDTO) o;
            return Objects.equals(box, that.box) &&
                    Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(box, text);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", PointPDFTextStripperTest.PointTextDTO.class.getSimpleName() + "[", "]")
                    .add("box=" + box)
                    .add("text='" + text + "'")
                    .toString();
        }
    }

}
