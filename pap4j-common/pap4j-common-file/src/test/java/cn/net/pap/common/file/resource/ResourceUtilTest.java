package cn.net.pap.common.file.resource;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class ResourceUtilTest {

    @Test
    public void copyResourcesTest() throws IOException {
        Path tempDir = Files.createTempDirectory("copyResourcesTest-");
        try {
            ResourceUtil.copyNativeResources(tempDir.toAbsolutePath().toString());
        } finally {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

}
