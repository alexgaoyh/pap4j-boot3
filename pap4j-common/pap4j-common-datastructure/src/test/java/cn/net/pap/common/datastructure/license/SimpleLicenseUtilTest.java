package cn.net.pap.common.datastructure.license;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class SimpleLicenseUtilTest {

    @Test
    public void gene() throws Exception {
        Map<String, String> licenseProps = new HashMap<>();
        licenseProps.put("COMPANY", "Test Corp");
        licenseProps.put("ISSUEDATE", "2025-04-17");
        licenseProps.put("EXPIRY", "2025-04-25");
        licenseProps.put("TYPE", "PREMIUM");
        String signedLicense = SimpleLicenseUtil.generateSignedLicense(licenseProps);
        System.out.println(signedLicense);
    }

    @Test
    public void check() throws Exception {
        Map<String, String> licenseProps = new HashMap<>();
        licenseProps.put("COMPANY", "Test Corp");
        licenseProps.put("ISSUEDATE", "2025-04-17");
        licenseProps.put("EXPIRY", "2025-04-25");
        licenseProps.put("TYPE", "PREMIUM");
        String signedLicense = SimpleLicenseUtil.generateSignedLicense(licenseProps);

        String[] args = new String[1];
        args[0] = "--license=" + signedLicense;

        // spring boot 项目的 main 方法的验证
        boolean b = SimpleLicenseUtil.checkLicense(args);
        System.out.println(b);
    }


}
