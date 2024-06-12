package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.CoordsDTO;
import cn.net.pap.common.pdf.dto.PointDTO;
import cn.net.pap.common.pdf.enums.ChineseFont;
import cn.net.pap.common.pdf.sign.SignatureInterfaceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.List;

public class PDFUtil {

    private static final float PDF_VERSION = 1.4f;
    private static final String PDF_PART = "1";
    private static final String PDF_CONFORMANCE = "A";

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
