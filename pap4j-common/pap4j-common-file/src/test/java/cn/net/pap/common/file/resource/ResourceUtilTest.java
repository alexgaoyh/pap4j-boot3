package cn.net.pap.common.file.resource;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceUtilTest {

    @Test
    public void copyResourcesTest() throws IOException {
        Path tempDir = Files.createTempDirectory("copyResourcesTest-");
        ResourceUtil.copyNativeResources(tempDir.toAbsolutePath().toString());
        new File(tempDir.toAbsolutePath().toString()).deleteOnExit();
    }

}
