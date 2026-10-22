package cn.net.pap.common.core.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class TestResourceUtilTest {

    @Test
    public void testGetFile() throws Exception {
        File file = TestResourceUtil.getFile("test-resource.txt");
        assertNotNull(file);
        assertTrue(file.exists());
        String content = Files.readString(file.toPath());
        assertTrue(content.contains("hello test resource"));
    }

    @Test
    public void testGetFileWithSlash() throws Exception {
        File file = TestResourceUtil.getFile("/test-resource.txt");
        assertNotNull(file);
        assertTrue(file.exists());
    }

    @Test
    public void testGetFileWithClass() throws Exception {
        File file = TestResourceUtil.getFile(TestResourceUtilTest.class, "test-resource.txt");
        assertNotNull(file);
        assertTrue(file.exists());
    }

    @Test
    public void testGetFileNotFound() {
        assertThrows(RuntimeException.class, () -> TestResourceUtil.getFile("non-existent-resource-file.xyz"));
    }

    @Test
    public void testCreateTempFile() throws Exception {
        File temp = TestResourceUtil.createTempFile("test_prefix_", ".tmp");
        assertNotNull(temp);
        assertTrue(temp.exists());
    }

    @Test
    public void testGetNonExistentPath() {
        String path = TestResourceUtil.getNonExistentPath("foo.bar");
        assertNotNull(path);
        assertFalse(new File(path).exists());
    }
}
