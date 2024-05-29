package cn.net.pap.common.pdf.sign;

import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;

public class SignatureInterfaceImpl implements SignatureInterface {
    private PrivateKey privateKey;

    public SignatureInterfaceImpl(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            //使用Bouncycastle进行数字签名
            Security.addProvider(new BouncyCastleProvider());
            Signature signature = Signature.getInstance("SHA256withRSA", "BC");
            signature.initSign(privateKey);
            byte[] buffer = new byte[8192];
            int length = 0;
            while ((length = content.read(buffer)) != -1) {
                signature.update(buffer, 0, length);
            }
            return signature.sign();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
