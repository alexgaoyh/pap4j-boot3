package cn.net.pap.common.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TempDirUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(TempDirUtilsTest.class);

    @BeforeEach
    void setUp() {
        System.setProperty("temp.dir.utils.dev", "false");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("temp.dir.utils.dev");
    }

    @Test
    public void testTempFileLifecycle() throws Exception {
        final Path[] recordedPath = new Path[1];
        String result = TempDirUtils.withTempFile("core-test-", tempFile -> {
            recordedPath[0] = tempFile;
            assertTrue(Files.exists(tempFile), "临时文件应该在回调期间存在");
            try {
                Files.writeString(tempFile, "core-content");
                return Files.readString(tempFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue("core-content".equals(result));
        assertFalse(Files.exists(recordedPath[0]), "回调执行完毕后临时文件应被自动清理");
    }

    @Test
    public void testTempDirLifecycle() throws Exception {
        final Path[] recordedDir = new Path[1];
        TempDirUtils.withTempDir("core-dir-", tempDir -> {
            recordedDir[0] = tempDir;
            assertTrue(Files.exists(tempDir), "临时目录应该在回调期间存在");
            try {
                Path childFile = tempDir.resolve("child.txt");
                Files.writeString(childFile, "child-content");
                assertTrue(Files.exists(childFile));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertFalse(Files.exists(recordedDir[0]), "回调执行完毕后临时目录及其子文件应被递归清理");
    }
}
