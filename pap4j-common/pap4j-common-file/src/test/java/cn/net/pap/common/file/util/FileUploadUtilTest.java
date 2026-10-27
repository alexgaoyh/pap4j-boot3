package cn.net.pap.common.file.util;

import cn.net.pap.common.file.dto.FileDTO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileUploadUtil} 测试。
 */
public class FileUploadUtilTest {

    /**
     * 最基本的本地上传：内容落盘、size 与元数据正确。
     *
     * @throws IOException 读取落盘文件失败
     */
    @Test
    public void uploadWritesContent() throws IOException {
        byte[] content = "hello pap4j".getBytes(StandardCharsets.UTF_8);
        FileDTO dto = FileUploadUtil.upload(new ByteArrayInputStream(content), "fs1", 1L, "biz-1", "test.txt");

        // filePath 形如 fs1:8e/fc/<32位token>，去掉实例名前缀后拼到本地根目录即为物理路径
        Path target = Path.of(System.getProperty("java.io.tmpdir"), "pap-file")
                .resolve(dto.filePath().substring(dto.filePath().indexOf(':') + 1));

        assertEquals(1L, dto.id());
        assertEquals("biz-1", dto.bizId());
        assertEquals("test.txt", dto.origName());
        assertEquals(content.length, dto.size());
        assertEquals(dto.createdAt(), dto.updatedAt());
        assertNull(dto.deletedAt());
        assertTrue(dto.filePath().startsWith("fs1:"));

        assertTrue(Files.isRegularFile(target), "落盘文件必须存在: " + target);
        assertArrayEquals(content, Files.readAllBytes(target));
    }

    /**
     * 测试通过 openStream(String) 读取已上传文件。
     */
    @Test
    public void openStreamReadsUploadedFile() throws IOException {
        byte[] content = "test open stream content".getBytes(StandardCharsets.UTF_8);
        FileDTO dto = FileUploadUtil.upload(new ByteArrayInputStream(content), "fs1", 2L, "biz-2", "read-test.txt");

        try (InputStream in = FileUploadUtil.openStream(dto.filePath(), 1, 100)) {
            assertNotNull(in);
            assertArrayEquals(content, in.readAllBytes());
        }
    }
}
