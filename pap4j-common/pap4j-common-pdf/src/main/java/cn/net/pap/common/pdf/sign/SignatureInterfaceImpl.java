package cn.net.pap.common.pdf.sign;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public class SignatureInterfaceImpl implements SignatureInterface {
    private final PrivateKey privateKey;
    private final Certificate[] certificateChain;

    public SignatureInterfaceImpl(PrivateKey privateKey, Certificate[] certificateChain) {
        this.privateKey = privateKey;
        this.certificateChain = certificateChain;
    }

    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            byte[] contentBytes = IOUtils.toByteArray(content);
            return signContent(contentBytes, privateKey, certificateChain);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static byte[] signContent(byte[] content, PrivateKey privateKey, Certificate[] certificateChain) throws Exception {
        // 用于生成PKCS#7签名的代码，这里仅为示例，具体实现可能需要使用BouncyCastle或其他库
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
        generator.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder().build()
                ).build(signer, (X509Certificate) certificateChain[0])
        );
        Store<?> certStore = new JcaCertStore(Arrays.asList(certificateChain));
        generator.addCertificates(certStore);
        CMSProcessableByteArray contentBytes = new CMSProcessableByteArray(content);
        CMSSignedData signedData = generator.generate(contentBytes, false);
        return signedData.getEncoded();
    }
}
