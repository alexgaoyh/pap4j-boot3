package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.sign.SignatureInterfaceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Calendar;

public class PDFUtil {

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
}
