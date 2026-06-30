package cn.net.pap.example.proguard.diagnostics;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <p><b>ApiRouterCatalogExporterTest</b></p>
 * <p>
 * 本类作为 Java Web 接口契约的“自动导出器”，在单元测试阶段直接导出标准的 OpenAPI JSON 契约文件至项目根目录的 .ai/openapi 目录下（以当前子模块名称命名），为 AI 编码助手提供“API 契约活地图”。
 * </p>
 */
@SpringBootTest(
    classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class},
    properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class ApiRouterCatalogExporterTest {

    private static final Logger log = LoggerFactory.getLogger(ApiRouterCatalogExporterTest.class);

    private final MockMvc mockMvc;

    public ApiRouterCatalogExporterTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    public void exportApiCatalog() throws Exception {
        String responseContent = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        File rootDir = findProjectRoot();
        if (rootDir != null) {
            File openapiDir = new File(rootDir, ".ai/openapi");
            if (!openapiDir.exists()) {
                openapiDir.mkdirs();
            }
            String moduleName = getModuleName();
            File targetFile = new File(openapiDir, moduleName + ".json");
            if (targetFile.exists()) {
                targetFile.delete();
            }
            Files.writeString(targetFile.toPath(), responseContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("OpenAPI JSON 契约文件已成功导出至: {}", targetFile.getAbsolutePath());
        } else {
            log.warn("未找到包含 .ai 目录的项目根路径，API 契约导出失败");
        }
    }

    private File findProjectRoot() {
        File currentDir = new File(".").getAbsoluteFile();
        while (currentDir != null) {
            if (new File(currentDir, ".ai").isDirectory() || new File(currentDir, ".agent").isDirectory()) {
                return currentDir;
            }
            currentDir = currentDir.getParentFile();
        }
        return null;
    }

    private String getModuleName() {
        try {
            java.net.URL url = ApiRouterCatalogExporterTest.class.getProtectionDomain().getCodeSource().getLocation();
            File path = new File(url.toURI());
            while (path != null) {
                if (new File(path, "pom.xml").isFile()) {
                    return path.getName();
                }
                path = path.getParentFile();
            }
        } catch (Exception e) {
            // fallback
        }
        return new File(".").getAbsoluteFile().getName();
    }
}
