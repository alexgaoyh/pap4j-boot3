package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.CoordsDTO;
import cn.net.pap.common.pdf.dto.PointDTO;
import cn.net.pap.common.pdf.enums.ChineseFont;
import cn.net.pap.common.pdf.sign.SignatureInterfaceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class PDFUtil {

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
            return false;
        }
    }

    /**
     * 添加印章
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
     * @param inputFilePath
     * @param outputFilePath
     * @throws Exception
     */
    public static void convertPDFA(final String inputFilePath, final String outputFilePath)
            throws Exception {
        final File inputFile = new File(inputFilePath);
        final File outputFile = new File(outputFilePath);

        byte[] inputContent = Files.readAllBytes(inputFile.getAbsoluteFile().toPath());

        final InputStream colorSpaceProfileInputStream = PDFUtil.class.getClassLoader().getResourceAsStream("sRGB Color Space Profile.icm");

        PDDocument doc = Loader.loadPDF(inputContent);

        PDDocumentCatalog catalog = setCompliant(doc, PDF_PART, PDF_CONFORMANCE);

        addOutputIntent(doc, catalog, colorSpaceProfileInputStream);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        doc.setVersion(PDF_VERSION);
        doc.save(byteArrayOutputStream);
        doc.close();

        final byte[] outputContent = byteArrayOutputStream.toByteArray();
        try (final OutputStream outputStream = Files.newOutputStream(outputFile.toPath())) {
            outputStream.write(outputContent);
        }

    }

    /**
     * 写文字，支持字体大小和按照写入顺序读取.
     * @param pdfPath
     * @param coordsDTOList
     * @throws IOException
     */
    public static void drawText(String pdfPath, List<CoordsDTO> coordsDTOList) throws IOException {
        // 创建或加载PDF文档
        try (PDDocument document = new PDDocument()) {
            // 仿宋
            PDType0Font simfangFont = PDType0Font.load(document, PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("仿宋")));
            // 创建新页面
            PDPage page = new PDPage();
            document.addPage(page);

            // 获取页面内容流
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                for(CoordsDTO coordsDTO : coordsDTOList) {
                    String text = coordsDTO.getText();

                    float x = coordsDTO.getX();
                    float y = coordsDTO.getY();
                    float width = coordsDTO.getWidth();
                    float height = coordsDTO.getHeight();

                    // 计算文字宽度
                    float textWidth = simfangFont.getStringWidth(text) / 1000 * 12;
                    // 计算文字高度
                    float textHeight = simfangFont.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * 12;
                    // 计算缩放比例，确保文字填充至整个矩形区域
                    float scalingFactor = Math.min(width / textWidth, height / textHeight);

                    contentStream.beginText();
                    contentStream.setFont(simfangFont, 12 * scalingFactor);
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
     * @param text
     * @return
     */
    public static PDType0Font findFont(String text) {
        try (PDDocument document = new PDDocument()) {
            for(ChineseFont chineseFont : ChineseFont.values()) {
                PDType0Font tmp = PDType0Font.load(document, PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation(chineseFont.getFontName())));
                try {
                    if(tmp.getStringWidth(String.valueOf(text)) > 0) {
                        return tmp;
                    }
                } catch (Exception e) {

                }
            }
        } catch (IOException e) {

        }
        return null;
    }

    /**
     * 写入段落
     * @param pdfPath
     * @param paragraphs
     * @throws IOException
     */
    public static void drawParagraphs(String pdfPath, List<String> paragraphs) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // 加载字体
            PDType0Font songFont = PDType0Font.load(document, PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("宋体")));
            PDType0Font songFontExtB = PDType0Font.load(document, PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("宋体ExtB")));

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
        PDDocument document = new PDDocument();
        try {
            for (String imagePath : imagePaths) {
                PDImageXObject imageXObject = PDImageXObject.createFromFile(imagePath, document);

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
            return false;
        } finally {
            try {
                document.close();
            } catch (IOException e) {
                return false;
            }
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

        final InputStream is = PDFUtil.class.getClassLoader().getResourceAsStream("xmpTemplate.xml");

        final byte[] fileBytes = IOUtils.toByteArray(is);

        String content = new String(fileBytes, charset);
        content = content.replace("@#pdfaid:part#@", pdfPart);
        content = content.replace("@#pdfaid:conformance#@", pdfConformance);

        is.close();

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

}
