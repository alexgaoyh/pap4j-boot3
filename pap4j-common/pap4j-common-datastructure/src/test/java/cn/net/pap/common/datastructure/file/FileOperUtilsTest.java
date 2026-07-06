package cn.net.pap.common.datastructure.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileOperUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    public void testDeleteDirectory() throws IOException {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);
        Path file = subDir.resolve("test.txt");
        Files.writeString(file, "content");

        assertTrue(Files.exists(subDir));
        assertTrue(Files.exists(file));

        FileOperUtils.deleteDirectory(subDir.toAbsolutePath().toString());

        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(file));
    }

    @Test
    public void testWriteAtomically() throws IOException {
        Path targetFile = tempDir.resolve("test_atomic.txt");
        String content = "Hello Atomic World!";
        ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        Path resultPath = FileOperUtils.writeAtomically(bais, targetFile);

        assertEquals(targetFile.toAbsolutePath(), resultPath.toAbsolutePath());
        assertTrue(Files.exists(targetFile));
        assertEquals(content, Files.readString(targetFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testGenerateVersionedPath() {
        Path result = FileOperUtils.generateVersionedPath(tempDir, "test.png", "20260706135731123");
        assertEquals(tempDir.resolve("test_20260706135731123.png").normalize(), result);

        // 测试穿越防御
        assertThrows(IllegalArgumentException.class, () -> 
            FileOperUtils.generateVersionedPath(tempDir, "../test.png", "20260706135731123")
        );
        assertThrows(IllegalArgumentException.class, () -> 
            FileOperUtils.generateVersionedPath(tempDir, "sub/test.png", "20260706135731123")
        );
        assertThrows(IllegalArgumentException.class, () -> 
            FileOperUtils.generateVersionedPath(tempDir, "test.png", "../20260706")
        );
    }

    @Test
    public void testCleanObsoleteVersions() throws IOException {
        // 创建多个版本文件，并且加入一个前缀极易混淆的文件 logo_footer_20260706100000001.png，以验证绝对不会误删它
        Path v1 = tempDir.resolve("my_image_20260706100000001.png");
        Path v2 = tempDir.resolve("my_image_20260706100000002.png");
        Path v3 = tempDir.resolve("my_image_20260706100000003.png"); // 最新版本应该是 v3
        Path sibling = tempDir.resolve("my_image_footer_20260706100000001.png"); // 用于碰撞测试

        Files.writeString(v1, "Version 1");
        Files.writeString(v2, "Version 2");
        Files.writeString(v3, "Version 3");
        Files.writeString(sibling, "Sibling File - Should NOT be deleted");

        // 修改最后修改时间以进行测试
        long now = System.currentTimeMillis();
        // 让 v1 和 v2 属于“历史久远”的文件 (比如让其修改时间是 10 秒前)
        Files.setLastModifiedTime(v1, java.nio.file.attribute.FileTime.fromMillis(now - 10000));
        Files.setLastModifiedTime(v2, java.nio.file.attribute.FileTime.fromMillis(now - 10000));
        // 让 sibling 也同样很久远，以验证它即使在宽限期之外也不会被当成 my_image 删掉
        Files.setLastModifiedTime(sibling, java.nio.file.attribute.FileTime.fromMillis(now - 10000));
        // 让 v3 是刚刚修改的
        Files.setLastModifiedTime(v3, java.nio.file.attribute.FileTime.fromMillis(now));

        // 清理所有超过 5 秒未修改的旧版本 (即应该清理掉 v1 和 v2，而绝对保留最新版本 v3 以及不相干的 sibling)
        List<Path> deleted = FileOperUtils.cleanObsoleteVersions(tempDir, "my_image.png", 5000);

        assertEquals(2, deleted.size());
        assertTrue(deleted.contains(v1));
        assertTrue(deleted.contains(v2));
        assertFalse(deleted.contains(v3));
        assertFalse(deleted.contains(sibling));

        // 验证文件实际存在状态
        assertFalse(Files.exists(v1));
        assertFalse(Files.exists(v2));
        assertTrue(Files.exists(v3));
        assertTrue(Files.exists(sibling)); // 验证混淆前缀文件没有被删除
    }

    @Test
    public void testUploadAndVersionedCleanupWorkflow() throws IOException {
        String logicalName = "product_image.png";

        // 1. 模拟第一次上传 (Version = "20260706135701111")
        Path v1Path = FileOperUtils.generateVersionedPath(tempDir, logicalName, "20260706135701111");
        String v1Content = "Image Version 1 Content";
        FileOperUtils.writeAtomically(new ByteArrayInputStream(v1Content.getBytes(StandardCharsets.UTF_8)), v1Path);

        assertTrue(Files.exists(v1Path));
        assertEquals(v1Content, Files.readString(v1Path, StandardCharsets.UTF_8));

        // 2. 模拟第二次上传 (Version = "20260706135702222")
        Path v2Path = FileOperUtils.generateVersionedPath(tempDir, logicalName, "20260706135702222");
        String v2Content = "Image Version 2 Content";
        FileOperUtils.writeAtomically(new ByteArrayInputStream(v2Content.getBytes(StandardCharsets.UTF_8)), v2Path);

        assertTrue(Files.exists(v2Path));
        assertEquals(v2Content, Files.readString(v2Path, StandardCharsets.UTF_8));

        // 3. 模拟第三次上传 (Version = "20260706135703333")
        Path v3Path = FileOperUtils.generateVersionedPath(tempDir, logicalName, "20260706135703333");
        String v3Content = "Image Version 3 Content (Latest)";
        FileOperUtils.writeAtomically(new ByteArrayInputStream(v3Content.getBytes(StandardCharsets.UTF_8)), v3Path);

        assertTrue(Files.exists(v3Path));
        assertEquals(v3Content, Files.readString(v3Path, StandardCharsets.UTF_8));

        // 显式设置最后修改时间，确保排序正确 (v3 最接近当前，v1 最古老)
        long now = System.currentTimeMillis();
        Files.setLastModifiedTime(v1Path, java.nio.file.attribute.FileTime.fromMillis(now - 200));
        Files.setLastModifiedTime(v2Path, java.nio.file.attribute.FileTime.fromMillis(now - 100));
        Files.setLastModifiedTime(v3Path, java.nio.file.attribute.FileTime.fromMillis(now));

        // 此时，物理文件夹下存在 3 个版本的图片文件
        // 4. 执行垃圾回收清理旧版本 (宽限期设为 0，即立即清除所有历史老版本，无条件保留最新的 v3)
        List<Path> cleaned = FileOperUtils.cleanObsoleteVersions(tempDir, logicalName, 0L);

        // 验证清理结果
        assertEquals(2, cleaned.size());
        assertTrue(cleaned.contains(v1Path));
        assertTrue(cleaned.contains(v2Path));
        assertFalse(cleaned.contains(v3Path));

        // 验证物理存在状态：v1 和 v2 被清除，最新版本 v3 必须完好保留
        assertFalse(Files.exists(v1Path));
        assertFalse(Files.exists(v2Path));
        assertTrue(Files.exists(v3Path));
        assertEquals(v3Content, Files.readString(v3Path, StandardCharsets.UTF_8));
    }
}
