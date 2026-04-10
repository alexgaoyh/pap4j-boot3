package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.CoordsDTO;
import cn.net.pap.common.pdf.dto.PointDTO;
import cn.net.pap.common.pdf.enums.ChineseFont;
import cn.net.pap.common.pdf.jpg.JpegDPIProcessor;
import cn.net.pap.common.pdf.sign.SignatureInterfaceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.*;
import java.util.stream.Collectors;

public class PDFUtil {

    private static final Logger log = LoggerFactory.getLogger(PDFUtil.class);

    private static final float PDF_VERSION = 1.4f;
    private static final String PDF_PART = "1";
    private static final String PDF_CONFORMANCE = "A";

    /**
     * 用低内存方式提取 PDF 单页，避免一次性加载整个文件
     *
     * @param pdfFilePath PDF 文件路径
     * @param pageNum     要提取的页码（从1开始）
     * @return 单页 PDF 的 ByteArrayOutputStream
     * @throws IOException IO异常或非法页码
     */
    public static ByteArrayOutputStream extractPage(String pdfFilePath, int pageNum) throws IOException {
        try (RandomAccessRead randomAccessRead = new RandomAccessReadBufferedFile(pdfFilePath);
             PDDocument document = Loader.loadPDF(randomAccessRead)) {
            try (PDDocument singlePageDoc = new PDDocument()) {
                singlePageDoc.addPage(document.getPage(pageNum - 1));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                singlePageDoc.save(outputStream);
                return outputStream;
            }
        }
    }

    /**
     * 单页 PDF 转换 JPG
     * <p><b>警告 (存在 Bug):</b> 虽然注释是单页转换，但实际代码会遍历所有页并反复覆盖同一个 outputPath。
     * 如果传入多页 PDF，最终只会得到最后一页的图片，且浪费渲染性能。
     * 多页转换建议使用 {@link #extractImagesToJPG}。</p>
     *
     * @param pdfFilePath   PDF文件绝对路径
     * @param outputPath    JPG文件绝对路径
     * @param DPI           DPI
     */
    public static boolean convertPDFToJPG(String pdfFilePath, String outputPath, Integer DPI) {
        try (PDDocument document = Loader.loadPDF(new File(pdfFilePath))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            // 遍历每一页
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, DPI);
                File outputfile = new File(outputPath);
                ImageIO.write(bufferedImage, "jpg", outputfile);
            }

            return true;
        } catch (IOException e) {
            log.error("convertPDFToJPG error", e);
            return false;
        }
    }

    /**
     * 添加印章
     * <p><b>注意 (硬编码):</b> 目前代码将印章固定添加在第一页 (getPage(0)) 且坐标写死为 (100, 100)。
     * 生产环境通常需要根据关键字或指定坐标、指定页码灵活处理，直接复用可能无法满足业务需求。</p>
     *
     * @param pdfFilePath
     * @param imageFilePath
     * @param outputFilePath
     * @throws Exception
     */
    public static void addStamp(String pdfFilePath, String imageFilePath, String outputFilePath) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(pdfFilePath))) {
            PDPage page = document.getPage(0);  // 在第一页添加印章

            PDImageXObject pdImage = PDImageXObject.createFromFile(imageFilePath, document);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                contentStream.drawImage(pdImage, 100, 100, pdImage.getWidth(), pdImage.getHeight());
            }

            document.save(outputFilePath);
        }
    }

    /**
     * 添加数字签名
     * <p><b>注意 (硬编码):</b> 方法内部对签名的名称 (name)、位置 (location) 和原因 (reason) 进行了硬编码。
     * 生产环境中这些信息通常需要作为参数动态传入。</p>
     *
     * @param pdfFilePath
     * @param keystorePath     *.p12   可以申请SSL证书(*.key, .crt)，然后使用openssl 命令转换为 p12 证书
     * @param keystorePassword 密码
     * @param outputFilePath
     * @throws Exception
     */
    public static void addSign(String pdfFilePath, String keystorePath, String keystorePassword, String outputFilePath) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(pdfFilePath))) {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keystore.load(fis, keystorePassword.toCharArray());
            }
            String alias = keystore.aliases().nextElement();
            PrivateKey privateKey = (PrivateKey) keystore.getKey(alias, keystorePassword.toCharArray());
            Certificate[] certificateChain = keystore.getCertificateChain(alias);

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("name");
            signature.setLocation("location");
            signature.setReason("reason");
            signature.setSignDate(Calendar.getInstance());

            SignatureOptions signatureOptions = new SignatureOptions();
            signatureOptions.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE);
            document.addSignature(signature, new SignatureInterfaceImpl(privateKey, certificateChain), signatureOptions);

            try (FileOutputStream fos = new FileOutputStream(outputFilePath);) {
                ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(fos);
                byte[] content = IOUtils.toByteArray(externalSigning.getContent());
                byte[] signedContent = SignatureInterfaceImpl.signContent(content, privateKey, certificateChain);
                externalSigning.setSignature(signedContent);
            }
        }
    }

    /**
     * 添加禁止编辑(密码)
     * <p><b>注意 (硬编码):</b> 加密级别硬编码为 128 位 RC4。
     * 对于普通业务（仅禁止编辑）足够，但现代高安全性 PDF 推荐使用 256 位 AES。</p>
     *
     * @param pdfFilePath
     * @param ownerPassword
     * @param userPassword
     * @param outputFilePath
     * @throws Exception
     */
    public static void addProtect(String pdfFilePath, String ownerPassword, String userPassword, String outputFilePath) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(pdfFilePath))) {
            AccessPermission accessPermission = new AccessPermission();
            accessPermission.setCanModify(false);  // 禁止编辑

            StandardProtectionPolicy protectionPolicy = new StandardProtectionPolicy(ownerPassword, userPassword, accessPermission);
            protectionPolicy.setEncryptionKeyLength(128);
            document.protect(protectionPolicy);

            document.save(outputFilePath);
        }
    }

    /**
     * convert PDF/A
     * <p><b>警告 (伪 PDF/A):</b> 该方法仅仅替换了元数据 (XMP Metadata) 和声明了颜色空间 (OutputIntent)，
     * 并未真正执行 PDF/A 要求的严格转换（如全量嵌入字体、移除透明度等）。
     * 转换后的文件在专业的 PDF/A 校验器中可能会报错，无法满足严格的归档合规要求。</p>
     *
     * @param inputFilePath
     * @param outputFilePath
     * @throws Exception
     */
    public static void convertPDFA(final String inputFilePath, final String outputFilePath)
            throws Exception {
        final File inputFile = new File(inputFilePath);
        final File outputFile = new File(outputFilePath);

        try(final InputStream colorSpaceProfileInputStream = PDFUtil.class.getClassLoader().getResourceAsStream("sRGB Color Space Profile.icm");
            final RandomAccessRead randomAccessRead = new RandomAccessReadBufferedFile(inputFile);
            final PDDocument doc = Loader.loadPDF(randomAccessRead)) {

            PDDocumentCatalog catalog = setCompliant(doc, PDF_PART, PDF_CONFORMANCE);

            addOutputIntent(doc, catalog, colorSpaceProfileInputStream);

            doc.setVersion(PDF_VERSION);
            
            try (final OutputStream outputStream = Files.newOutputStream(outputFile.toPath())) {
                doc.save(outputStream);
            }
        }

    }

    /**
     * 写文字，支持字体大小和按照写入顺序读取.
     * <p><b>警告 (覆盖原文件):</b> 该方法并不是在现有的 PDF 上追加内容，而是直接创建一个全新的空文档并写入内容，
     * 最终会覆盖目标路径的文件。如果需要在已有 PDF 上追加，请重写此方法（以 AppendMode.APPEND 模式打开流）。</p>
     *
     * @param pdfPath
     * @param coordsDTOList
     * @throws IOException
     */
    public static void drawText(String pdfPath, List<CoordsDTO> coordsDTOList, Integer... widthAndHeight) throws IOException {
        // 创建或加载PDF文档
        try (PDDocument document = new PDDocument()) {
            // 仿宋 PDType0Font.load 第三个参数默认为 true,  表示字体是子集嵌入（只嵌入用了的字符集） 通常子集会比完整字体小很多
            // 使用 try-with-resources 关闭流防止内存泄露
            PDType0Font simfangFont;
            try (InputStream is = PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("仿宋"))) {
                simfangFont = PDType0Font.load(document, is);
            }
            // 创建新页面
            PDPage page = null;
            if(widthAndHeight != null && widthAndHeight.length == 2) {
                page = new PDPage(new PDRectangle(widthAndHeight[0],	widthAndHeight[1]));
            } else {
                page = new PDPage();
            }
            document.addPage(page);

            List<Map<String, Object>> loadedFontMaps = new ArrayList<>();
            Map<String, Object> simfangFontMap = new HashMap<>();
            simfangFontMap.put("fontName", "仿宋");
            simfangFontMap.put("PDType0Font", simfangFont);
            loadedFontMaps.add(simfangFontMap);

            // 获取页面内容流
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                for(CoordsDTO coordsDTO : coordsDTOList) {
                    String text = coordsDTO.getText();

                    float x = coordsDTO.getX();
                    float y = coordsDTO.getY();
                    float width = coordsDTO.getWidth();
                    float height = coordsDTO.getHeight();

                    // 将当前 document 传递给内部字体查找方法，确保字体绑定到正确的 document，从而满足 PDF/A 的字体嵌入要求
                    PDType0Font font = findFontInternal(document, text, loadedFontMaps);
                    if(font == null) {
                        // todo maybe throw exception
                        log.info("不匹配 ：{}", text);
                        text = "=";
                        font = simfangFont;
                    }

                    // 计算文字宽度
                    float textWidth = font.getStringWidth(text) / 1000 * 12;
                    // 计算文字高度
                    float textHeight = font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * 12;
                    // 计算缩放比例，确保文字填充至整个矩形区域
                    float scalingFactor = Math.min(width / textWidth, height / textHeight);

                    contentStream.beginText();
                    contentStream.setFont(font, 12 * scalingFactor);
                    contentStream.newLineAtOffset(x, y);
                    contentStream.showText(text);
                    contentStream.endText();
                }
            }

            // 保存PDF文档
            document.save(pdfPath);
        }
    }


    /**
     * 查询可用的字体
     * <p><b>致命错误 (流已关闭):</b> 在 try-with-resources 中创建并关闭了 PDDocument。
     * 返回的 PDType0Font 绑定在一个已关闭的文档上，后续在写入并执行 document.save() 时必然会触发 IOException。
     * <b>绝对不要在生产环境使用。</b> 请使用内部重构后的 findFontInternal。</p>
     *
     * @param text
     * @return
     * @deprecated 返回的字体绑定到了一个已关闭的 PDDocument，会导致 PDF/A 字体嵌入失败及保存报错。
     */
    @Deprecated
    public static PDType0Font findFont(String text) {
        try (PDDocument document = new PDDocument()) {
            for(ChineseFont chineseFont : ChineseFont.values()) {
                //  PDType0Font.load 第三个参数默认为 true,  表示字体是子集嵌入（只嵌入用了的字符集） 通常子集会比完整字体小很多
                // 修复：关闭输入流防止内存泄漏
                try (InputStream is = PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation(chineseFont.getFontName()))) {
                    if (is != null) {
                        PDType0Font tmp = PDType0Font.load(document, is);
                        try {
                            if(tmp.getStringWidth(String.valueOf(text)) > 0) {
                                return tmp;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to get string width for text: {}", text, e);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to load font: {}", chineseFont.getFontName(), e);
                }
            }
        } catch (IOException e) {
            log.error("findFont IOException", e);
        }
        return null;
    }

    /**
     * 内部字体查找方法，确保加载的字体绑定到正确的 PDDocument 以支持 PDF/A 嵌入
     */
    private static PDType0Font findFontInternal(PDDocument document, String text, List<Map<String, Object>> loadedFonts) {
        try {
            for(Map<String, Object> fontMap : loadedFonts) {
                if(((PDType0Font)fontMap.get("PDType0Font")).getStringWidth(String.valueOf(text)) > 0) {
                    return (PDType0Font)fontMap.get("PDType0Font");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get string width from loaded fonts", e);
        }

        for(ChineseFont chineseFont : ChineseFont.values()) {
            if(loadedFonts.stream().map(e->e.get("fontName").toString()).collect(Collectors.joining()).contains(chineseFont.getFontName())) {
               continue;
            }
            //  PDType0Font.load 第三个参数默认为 true,  表示字体是子集嵌入（只嵌入用了的字符集） 通常子集会比完整字体小很多
            InputStream resourceAsStream = PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation(chineseFont.getFontName()));
            if(resourceAsStream == null) {
                resourceAsStream = PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation(chineseFont.getFontName()).substring(1));
            }
            if(resourceAsStream != null) {
                try {
                    // 使用正确的 document 加载字体，保证能够成功进行子集嵌入并满足 PDF/A 标准
                    PDType0Font tmp = PDType0Font.load(document, resourceAsStream);
                    Map<String, Object> tmpFontMap = new HashMap<>();
                    tmpFontMap.put("fontName", chineseFont.getFontName());
                    tmpFontMap.put("PDType0Font", tmp);
                    loadedFonts.add(tmpFontMap);
                    if(tmp.getStringWidth(String.valueOf(text)) > 0) {
                        return tmp;
                    }
                } catch (Exception e) {
                    log.warn("Error loading or checking font: {}", chineseFont.getFontName(), e);
                } finally {
                    try {
                        resourceAsStream.close();
                    } catch (IOException e) {
                        log.warn("Error closing font input stream", e);
                    }
                }
            }

        }
        return null;
    }

    /**
     * 写入段落
     * <p><b>警告 (覆盖原文件):</b> 该方法会创建一个全新的空文档并覆盖目标路径的文件，
     * 而不是在现有文档上追加段落。同时该方法强依赖 ClassPath 下存在对应的“宋体”字体文件，否则会报错。</p>
     *
     * @param pdfPath
     * @param paragraphs
     * @throws IOException
     */
    public static void drawParagraphs(String pdfPath, List<String> paragraphs) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // 加载字体  PDType0Font.load 第三个参数默认为 true,  表示字体是子集嵌入（只嵌入用了的字符集） 通常子集会比完整字体小很多
            // 使用 try-with-resources 关闭流防止内存泄露，并确保字体绑定到当前 document 用于 PDF/A 的子集嵌入
            PDType0Font songFont;
            try (InputStream is = PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("宋体"))) {
                songFont = PDType0Font.load(document, is);
            }
            PDType0Font songFontExtB;
            try (InputStream is = PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("宋体ExtB"))) {
                songFontExtB = PDType0Font.load(document, is);
            }

            // 页面设置
            PDRectangle pageSize = PDRectangle.A4;
            float margin = 50;
            float width = pageSize.getWidth() - 2 * margin;
            float startX = margin;
            float startY = pageSize.getHeight() - margin;

            // 字体大小和行间距
            float fontSize = 12;
            float leading = 1.5f * fontSize;

            PDPage page = new PDPage(pageSize);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.beginText();
            contentStream.newLineAtOffset(startX, startY);

            for (String paragraph : paragraphs) {
                String[] lines = wrapText(paragraph, songFont, songFontExtB, fontSize, width);

                for (String line : lines) {
                    if (startY - leading < margin) {
                        // 结束当前页面的内容流
                        contentStream.endText();
                        contentStream.close();

                        // 添加新页面
                        page = new PDPage(pageSize);
                        document.addPage(page);

                        // 创建新的内容流
                        contentStream = new PDPageContentStream(document, page);
                        contentStream.beginText();
                        contentStream.newLineAtOffset(startX, pageSize.getHeight() - margin);
                        startY = pageSize.getHeight() - margin;
                    }

                    char[] chars = line.toCharArray();
                    for (int i = 0; i < chars.length;) {
                        String c = "";
                        if (Character.isHighSurrogate(chars[i])) {
                            // 如果是代理项的高位，则跳过两个字符
                            c = new String(Character.toChars(Character.toCodePoint(chars[i], chars[i + 1])));
                            i += 2;
                        } else {
                            // 否则，只跳过一个字符
                            c = chars[i] + "";
                            i++;
                        }

                        PDType0Font currentFont = fontContainsCharacter(songFont, songFontExtB, c + "") ? songFont : songFontExtB;
                        contentStream.setFont(currentFont, fontSize);
                        contentStream.showText(String.valueOf(c));
                    }

                    contentStream.newLineAtOffset(0, -leading);
                    startY -= leading;
                }

                // 添加段落之间的间距
                //startY -= leading;
            }

            // 结束内容流
            contentStream.endText();
            contentStream.close();

            // 保存PDF文档
            document.save(pdfPath);
        }
    }

    /**
     * 字体是否包含文字
     * @param font
     * @param fontExtB
     * @param c
     * @return
     */
    private static boolean fontContainsCharacter(PDType0Font font, PDType0Font fontExtB, String c)  {
        try {
            return font.getStringWidth(String.valueOf(c)) > 0;
        } catch (Exception e) {
            log.warn("fontContainsCharacter error for character: {}", c, e);
            return false;
        }
    }

    private static String[] wrapText(String text, PDType0Font songFont, PDType0Font songFontExtB, float fontSize, float width) throws IOException {
        StringBuilder line = new StringBuilder();
        float spaceWidth = songFont.getStringWidth(" ") / 1000 * fontSize;

        java.util.List<String> lines = new java.util.ArrayList<>();
        float lineWidth = 0;

        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length;) {
            String c = "";
            if (Character.isHighSurrogate(chars[i])) {
                // 如果是代理项的高位，则跳过两个字符
                c = new String(Character.toChars(Character.toCodePoint(chars[i], chars[i + 1])));
                i += 2;
            } else {
                // 否则，只跳过一个字符
                c = chars[i] + "";
                i++;
            }

            if(c.equals("\t")) {
                continue;
            }

            // 选择合适的字体
            PDType0Font currentFont = fontContainsCharacter(songFont, songFontExtB, c) ? songFont : songFontExtB;
            float charWidth = currentFont.getStringWidth(String.valueOf(c)) / 1000 * fontSize;

            // 如果行为空，则直接添加字符
            if (line.length() == 0) {
                line.append(c);
                lineWidth = charWidth;
            } else {
                // 添加空格宽度
                float spaceAdjustment = line.length() > 0 ? spaceWidth : 0;
                if (lineWidth + spaceAdjustment + charWidth <= width) {
                    line.append(c);
                    lineWidth += charWidth + spaceAdjustment;
                } else {
                    // 当前行已满，添加到行列表
                    lines.add(line.toString());
                    // 开始新行
                    line = new StringBuilder(String.valueOf(c));
                    lineWidth = charWidth;
                }
            }
        }

        // 添加最后一行
        if (line.length() > 0) {
            lines.add(line.toString());
        }

        return lines.toArray(new String[0]);
    }

    /**
     * 画矩形， 或者是表格的一个长方形的框。 提供一个从左下角开始，左上角结束的逆时针的四个点坐标.
     * <p><b>警告 (覆盖原文件):</b> 该方法会创建一个全新的空文档并覆盖目标路径的文件，
     * 而不是在现有文档上追加矩形框。如果需要在已有 PDF 上画框，请重写此方法。</p>
     *
     * @param pdfPath
     * @param leftBottom    左下角
     * @param rightBottom   右下角
     * @param rightTop  右上角
     * @param leftTop   左上角
     * @throws IOException
     */
    public static void drawRectangleBy4Point(String pdfPath, PointDTO leftBottom, PointDTO rightBottom, PointDTO rightTop, PointDTO leftTop) throws IOException {
        // 创建或加载PDF文档
        try (PDDocument document = new PDDocument()) {
            // 创建新页面
            PDPage page = new PDPage();
            document.addPage(page);

            // 获取页面内容流
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // bottom
                contentStream.moveTo(leftBottom.getX(), leftBottom.getY());
                contentStream.lineTo(rightBottom.getX(), rightBottom.getY());
                contentStream.stroke();

                // right
                contentStream.moveTo(rightBottom.getX(), rightBottom.getY());
                contentStream.lineTo(rightTop.getX(), rightTop.getY());
                contentStream.stroke();

                // top
                contentStream.moveTo(leftTop.getX(), leftTop.getY());
                contentStream.lineTo(rightTop.getX(), rightTop.getY());
                contentStream.stroke();

                // left
                contentStream.moveTo(leftTop.getX(), leftTop.getY());
                contentStream.lineTo(leftBottom.getX(), leftBottom.getY());
                contentStream.stroke();
            }

            // 保存PDF文档
            document.save(pdfPath);
        }
    }

    public static Boolean jpg2Pdf(String jpgPath, String pdfPath) {
        List<String> imagePaths = Arrays.asList(new String[]{ jpgPath });
        try (PDDocument document = new PDDocument()) {
            for (String imagePath : imagePaths) {
                PDImageXObject imageXObject = PDImageXObject.createFromFile(imagePath, document);
                // todo 后续根据需求，这里做一下调整，避免生成的pdf过大，未完全验证，存在不同的格式，比如 jpg ,还有各种不同压缩方式的tif 等等
//            BufferedImage image = ImageIO.read(new File(imagePath));
//            PDImageXObject imageXObject = JPEGFactory.createFromImage(document, image, 0.7f); // 质量70%

                float imageWidth = imageXObject.getWidth();
                float imageHeight = imageXObject.getHeight();

                PDPage page = new PDPage(new PDRectangle(imageWidth, imageHeight));
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
                    contentStream.drawImage(imageXObject, 0, 0, imageWidth, imageHeight);

                }
            }
            document.save(pdfPath);
            return true;
        } catch (Exception e) {
            log.warn("jpg2Pdf error for", e);
            return false;
        }
    }

    public static Boolean dir2Pdf(String dirPath, String pdfPath, Integer DPI) {
        List<String> imagePaths = new ArrayList<>();
        File dirPathFile = new File(dirPath);
        if (dirPathFile.isDirectory()) {
            File[] files = dirPathFile.listFiles();
            for (File f : files) {
                if (!f.isDirectory()) {
                    if (f.getName().toLowerCase().endsWith("jpg")) {
                        imagePaths.add(f.getAbsolutePath());
                    }
                }
            }
        }

        PDDocument document = new PDDocument();
        try {
            for (String imagePath : imagePaths) {

                PDImageXObject imageXObject = PDImageXObject.createFromFile(imagePath, document);
                // todo 后续根据需求，这里做一下调整，避免生成的pdf过大，未完全验证，存在不同的格式，比如 jpg ,还有各种不同压缩方式的tif 等等
//            BufferedImage image = ImageIO.read(new File(imagePath));
//            PDImageXObject imageXObject = JPEGFactory.createFromImage(document, image, 0.7f); // 质量70%

                float imageWidth = imageXObject.getWidth();
                float imageHeight = imageXObject.getHeight();

                // 重新处理宽高
                imageWidth = imageWidth / DPI * 72;
                imageHeight = imageHeight / DPI * 72;

                PDPage page = new PDPage(new PDRectangle(imageWidth, imageHeight));
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
                    contentStream.drawImage(imageXObject, 0, 0, imageWidth, imageHeight);
                }
            }
            document.save(pdfPath);
            return true;
        } catch (Exception e) {
            log.warn("dir2Pdf error", e);
            return false;
        } finally {
            try {
                document.close();
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static PDDocumentCatalog setCompliant(final PDDocument doc, final String pdfPart,
                                                  final String pdfConformance) throws IOException, Exception {

        final PDDocumentCatalog catalog = doc.getDocumentCatalog();
        final PDDocumentInformation info = doc.getDocumentInformation();
        final PDMetadata metadata = new PDMetadata(doc);
        catalog.setMetadata(metadata);

        final PDDocumentInformation newInfo = new PDDocumentInformation();
        newInfo.setProducer(info.getProducer());
        newInfo.setAuthor(info.getAuthor());
        newInfo.setTitle(info.getTitle());
        newInfo.setSubject(info.getSubject());
        newInfo.setKeywords(info.getKeywords());
        doc.setDocumentInformation(newInfo);

        final Charset charset = StandardCharsets.UTF_8;

        String content;
        // 使用 try-with-resources 确保 InputStream 关闭，防止异常时发生内存泄漏
        try (InputStream is = PDFUtil.class.getClassLoader().getResourceAsStream("xmpTemplate.xml")) {
            final byte[] fileBytes = IOUtils.toByteArray(is);
            content = new String(fileBytes, charset);
        }

        content = content.replace("@#pdfaid:part#@", pdfPart);
        content = content.replace("@#pdfaid:conformance#@", pdfConformance);

        final byte[] editedBytes = content.getBytes(charset);
        metadata.importXMPMetadata(editedBytes);

        return catalog;
    }

    private static void addOutputIntent(final PDDocument doc, final PDDocumentCatalog catalog,
                                        final InputStream colorProfile) throws IOException {

        final String profile = "sRGB IEC61966-2.1";

        if (catalog.getOutputIntents().isEmpty()) {

            final PDOutputIntent outputIntent;
            outputIntent = new PDOutputIntent(doc, colorProfile);
            outputIntent.setInfo(profile);
            outputIntent.setOutputCondition(profile);
            outputIntent.setOutputConditionIdentifier(profile);
            outputIntent.setRegistryName("http://www.color.org");

            catalog.addOutputIntent(outputIntent);
        }

    }

    /**
     * 分析
     * @param filePath
     * @throws IOException
     */
    public static void analyzePdf(String filePath) throws IOException {
        File file = new File(filePath);
        try (PDDocument document = Loader.loadPDF(file)) {
            log.info("PDF 文件页数: {}", document.getNumberOfPages());

            long totalImageSize = 0;
            long totalFontSize = 0;
            long totalContentSize = 0;
            long totalFormXObjectSize = 0;

            Set<String> fontNames = new HashSet<>();
            int pageNum = 1;

            for (PDPage page : document.getPages()) {
                COSDictionary pageDict = page.getCOSObject();
                long pageContentSize = 0;

                for (Iterator<PDStream> it = page.getContentStreams(); it.hasNext(); ) {
                    PDStream stream = it.next();
                    pageContentSize += stream.getLength();
                }

                log.info("第 {} 页内容流大小: {} KB", pageNum, pageContentSize / 1024);
                totalContentSize += pageContentSize;

                ResourceStats stats = analyzeResources(pageDict, fontNames);
                totalImageSize += stats.imageSize;
                totalFontSize += stats.fontSize;
                totalFormXObjectSize += stats.formSize;

                pageNum++;
            }

            log.info("图像总大小: {} KB", totalImageSize / 1024);
            log.info("字体数量: {}", fontNames.size());
            log.info("字体名: {}", fontNames);
            log.info("嵌入字体总大小: {} KB", totalFontSize / 1024);
            log.info("内容流总大小: {} KB", totalContentSize / 1024);
            log.info("Form XObject 总大小: {} KB", totalFormXObjectSize / 1024);

            long totalKnown = totalImageSize + totalFontSize + totalContentSize + totalFormXObjectSize;
            log.info("已知资源总大小: {} KB", totalKnown / 1024);

            long fileSize = file.length();
            log.info("实际文件大小: {} KB", fileSize / 1024);
            log.info("未解释部分大小: {} KB", (fileSize - totalKnown) / 1024);
        }
    }

    private static ResourceStats analyzeResources(COSDictionary resourceDict, Set<String> fontNames) {
        long imageSize = 0;
        long fontSize = 0;
        long formSize = 0;

        if (resourceDict == null) return new ResourceStats();

        COSDictionary resources = (COSDictionary) resourceDict.getDictionaryObject(COSName.RESOURCES);
        if (resources == null) return new ResourceStats();

        // 图像和 Form XObject
        COSDictionary xObjects = (COSDictionary) resources.getDictionaryObject(COSName.XOBJECT);
        if (xObjects != null) {
            for (COSName key : xObjects.keySet()) {
                COSBase base = xObjects.getDictionaryObject(key);
                if (base instanceof COSObject) base = ((COSObject) base).getObject();

                if (base instanceof COSStream) {
                    COSStream stream = (COSStream) base;
                    COSName subtype = stream.getCOSName(COSName.SUBTYPE);
                    if (COSName.IMAGE.equals(subtype)) {
                        long size = stream.getLength();
                        imageSize += size;
                        String imageType = getImageFilterType(stream);
                        log.info("发现图像对象: {} - 类型: {} - 大小: {} KB", key.getName(), imageType, size / 1024);
                    } else if (COSName.FORM.equals(subtype)) {
                        long size = stream.getLength();
                        formSize += size;
                        log.info("发现 Form XObject: {} - 大小: {} KB", key.getName(), size / 1024);

                        // 递归分析
                        COSDictionary formResources = (COSDictionary) stream.getDictionaryObject(COSName.RESOURCES);
                        ResourceStats innerStats = analyzeResources(formResources, fontNames);
                        imageSize += innerStats.imageSize;
                        fontSize += innerStats.fontSize;
                        formSize += innerStats.formSize;
                    }
                }
            }
        }

        // 字体处理，包括 CIDFont 结构
        COSDictionary fonts = (COSDictionary) resources.getDictionaryObject(COSName.FONT);
        if (fonts != null) {
            for (COSName fontKey : fonts.keySet()) {
                COSBase fontBase = fonts.getDictionaryObject(fontKey);
                if (fontBase instanceof COSObject) fontBase = ((COSObject) fontBase).getObject();

                if (fontBase instanceof COSDictionary) {
                    COSDictionary fontDict = (COSDictionary) fontBase;
                    COSName baseFont = fontDict.getCOSName(COSName.BASE_FONT);
                    if (baseFont != null) fontNames.add(baseFont.getName());

                    COSName subType = fontDict.getCOSName(COSName.SUBTYPE);
                    if (COSName.TYPE0.equals(subType)) {
                        // CIDFontType0 / CIDFontType2 字体处理
                        COSArray descendantFonts = (COSArray) fontDict.getDictionaryObject(COSName.DESCENDANT_FONTS);
                        if (descendantFonts != null) {
                            for (int i = 0; i < descendantFonts.size(); i++) {
                                COSBase descFontBase = descendantFonts.getObject(i);
                                if (descFontBase instanceof COSObject) descFontBase = ((COSObject) descFontBase).getObject();

                                if (descFontBase instanceof COSDictionary) {
                                    COSDictionary descFontDict = (COSDictionary) descFontBase;
                                    COSBase descBase = descFontDict.getDictionaryObject(COSName.FONT_DESC);
                                    if (descBase instanceof COSObject) descBase = ((COSObject) descBase).getObject();

                                    fontSize += extractFontStreams(descBase);
                                }
                            }
                        }
                    } else {
                        // 常规字体
                        COSBase descBase = fontDict.getDictionaryObject(COSName.FONT_DESC);
                        if (descBase instanceof COSObject) descBase = ((COSObject) descBase).getObject();

                        fontSize += extractFontStreams(descBase);
                    }
                }
            }
        }

        return new ResourceStats(imageSize, fontSize, formSize);
    }

    /**
     * 图像类型
     * @param stream
     * @return
     */
    private static String getImageFilterType(COSStream stream) {
        COSBase filterObj = stream.getDictionaryObject(COSName.FILTER);
        if (filterObj == null) return "Unknown";

        List<String> filters = new ArrayList<>();
        if (filterObj instanceof COSName) {
            filters.add(((COSName) filterObj).getName());
        } else if (filterObj instanceof COSArray) {
            for (int i = 0; i < ((COSArray) filterObj).size(); i++) {
                COSBase filterEntry = ((COSArray) filterObj).getObject(i);
                if (filterEntry instanceof COSName) {
                    filters.add(((COSName) filterEntry).getName());
                }
            }
        }

        return guessImageType(filters);
    }

    private static String guessImageType(List<String> filters) {
        // 常见组合分析
        if (filters.contains("DCTDecode")) return "JPEG";
        if (filters.contains("JPXDecode")) return "JPEG 2000";
        if (filters.contains("JBIG2Decode")) return "JBIG2";
        if (filters.contains("CCITTFaxDecode")) return "TIFF (Fax)";
        if (filters.contains("FlateDecode") && filters.contains("ASCII85Decode")) return "PNG (可能)";
        if (filters.contains("FlateDecode")) return "无损图 (可能 PNG)";
        return "未知";
    }

    private static long extractFontStreams(COSBase fontDescBase) {
        long fontSize = 0;
        if (fontDescBase instanceof COSDictionary) {
            COSDictionary fontDesc = (COSDictionary) fontDescBase;
            for (COSName fileName : new COSName[]{COSName.FONT_FILE, COSName.FONT_FILE2, COSName.FONT_FILE3}) {
                COSBase fileBase = fontDesc.getDictionaryObject(fileName);
                if (fileBase instanceof COSObject) fileBase = ((COSObject) fileBase).getObject();

                if (fileBase instanceof COSStream) {
                    COSStream fontStream = (COSStream) fileBase;
                    long size = fontStream.getLength();
                    fontSize += size;
                    log.info("发现嵌入字体流: {} - 大小: {} KB", fileName.getName(), size / 1024);
                }
            }
        }
        return fontSize;
    }

    private static class ResourceStats {
        long imageSize;
        long fontSize;
        long formSize;

        public ResourceStats() {}

        public ResourceStats(long imageSize, long fontSize, long formSize) {
            this.imageSize = imageSize;
            this.fontSize = fontSize;
            this.formSize = formSize;
        }
    }

    /**
     * 拆分PDF文件为单页PDF，支持自定义起始页码
     * @param inputFilePath 输入PDF文件路径
     * @param outputPattern 输出文件模式
     * @param startPage 起始页码（从1开始）
     * @throws IOException 如果文件操作失败
     */
    public static void splitPDF(String inputFilePath, String outputPattern, int startPage) throws IOException {
        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()) {
            throw new IOException("输入文件不存在: " + inputFilePath);
        }
        try (PDDocument document = Loader.loadPDF(inputFile)) {
            PDPageTree pages = document.getPages();
            int totalPages = pages.getCount();

            for (int i = 0; i < totalPages; i++) {
                try (PDDocument newDocument = new PDDocument()) {
                    PDPage importedPage = newDocument.importPage(pages.get(i));
                    // 使用自定义起始页码
                    String outputFilePath = String.format(outputPattern, startPage + i);
                    Path outputPath = Paths.get(outputFilePath);
                    Files.createDirectories(outputPath.getParent());
                    newDocument.save(outputFilePath);
                }
            }
        }
    }

    /**
     * 将 PDF 的每一页光栅化渲染为 JPG 图片保存，支持自定义起始编号
     * <p><b>说明 (方法名易误解):</b> 方法名为 extractImagesToJPG，但实际逻辑是把整页渲染成图片 (Page to Image)，
     * 并不是把 PDF 内部嵌入的原图 (Image Resources) 单独无损提取出来。</p>
     *
     * @param inputFilePath 输入PDF文件路径
     * @param outputPattern 输出文件模式
     * @param dpi 图像分辨率
     * @param startNumber 起始编号
     * @throws IOException 如果文件操作失败
     */
    public static void extractImagesToJPG(String inputFilePath, String outputPattern, int dpi,
                                          int startNumber) throws IOException {
        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()) {
            throw new IOException("输入文件不存在: " + inputFilePath);
        }

        try (PDDocument document = Loader.loadPDF(inputFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();

            for (int i = 0; i < totalPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                JpegDPIProcessor processor = new JpegDPIProcessor();
                byte[] img = processor.setDPI(image, dpi);

                // 使用自定义起始编号
                String outputFilePath = String.format(outputPattern, startNumber + i);
                Path outputPath = Paths.get(outputFilePath);
                Files.createDirectories(outputPath.getParent());
                try (FileOutputStream outputStream = new FileOutputStream(String.valueOf(outputPath));){
                    outputStream.write(img);
                }
            }
        }
    }

    /**
     * 将文件夹下的 图像文件， 转换为一个 多页pdf
     * @param inputFolderPath
     * @param outputFilePath
     * @throws IOException
     */
    public static void convertImagesToPDF(String inputFolderPath, String outputFilePath) throws IOException {
        File inputFolder = new File(inputFolderPath);
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            throw new IOException("输入文件夹不存在或不是目录: " + inputFolderPath);
        }

        // 获取文件夹中的所有图像文件并按字典序排序
        File[] imageFiles = inputFolder.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".jpg");
        });

        if (imageFiles == null || imageFiles.length == 0) {
            throw new IOException("文件夹中没有找到图像文件: " + inputFolderPath);
        }

        // 按文件名字典序排序
        Arrays.sort(imageFiles, Comparator.comparing(File::getName));

        try (PDDocument document = new PDDocument()) {
            for (File imageFile : imageFiles) {
                try {
                    // 读取图像
                    BufferedImage image = ImageIO.read(imageFile);
                    if (image == null) {
                        log.error("无法读取图像文件: {}", imageFile.getName());
                        continue;
                    }

                    // 创建PDF页面，大小与图像相同
                    PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
                    document.addPage(page);

                    // 将图像转换为PDImageXObject
                    // 无损压缩
                    // PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
                    // 有损压缩
                    PDImageXObject pdImage = JPEGFactory.createFromImage(document, image, 0.7f);

                    // 将图像添加到页面
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
                    }
                } catch (IOException e) {
                    log.error("处理文件时出错: {}", imageFile.getName(), e);
                }
            }

            // 确保输出目录存在
            Path outputPath = Paths.get(outputFilePath);
            Files.createDirectories(outputPath.getParent());

            // 保存PDF
            document.save(outputFilePath);
        }
    }

}
