package cn.net.pap.common.vips;

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

    public static File createTempFile(String prefix, String suffix) throws Exception {
        File tempFile = File.createTempFile(prefix, suffix);
        tempFile.deleteOnExit();
        return tempFile;
    }

    public static String getNonExistentPath(String fileName) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File nonExistent = new File(tempDir, "non_existent_" + System.currentTimeMillis() + "_" + fileName);
        return nonExistent.getAbsolutePath();
    }
}
