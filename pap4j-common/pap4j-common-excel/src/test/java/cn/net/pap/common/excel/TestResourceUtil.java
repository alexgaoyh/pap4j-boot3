package cn.net.pap.common.excel;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TestResourceUtil {

    public static File getFile(String classpath) {
        try {
            InputStream is = TestResourceUtil.class.getResourceAsStream(classpath.startsWith("/") ? classpath : "/" + classpath);
            if (is == null) {
                throw new RuntimeException("Resource not found: " + classpath);
            }
            String suffix = "";
            int dotIdx = classpath.lastIndexOf('.');
            if (dotIdx > 0) {
                suffix = classpath.substring(dotIdx);
            }
            File tempFile = File.createTempFile("test_res_", suffix);
            tempFile.deleteOnExit();
            Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
