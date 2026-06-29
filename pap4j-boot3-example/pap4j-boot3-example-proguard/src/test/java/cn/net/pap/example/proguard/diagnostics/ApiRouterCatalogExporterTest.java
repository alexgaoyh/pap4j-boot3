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
 * 本类作为 Java Web 接口契约的“自动导出器”，在单元测试阶段直接导出标准的 OpenAPI JSON 契约文件至系统临时目录，为 AI 编码助手提供“API 契约活地图”。
 * </p>
 */
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
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

        File targetFile = File.createTempFile("openapi_", ".json");
        Files.writeString(targetFile.toPath(), responseContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("OpenAPI JSON 契约文件已成功导出至临时文件: {}", targetFile.getAbsolutePath());
    }
}
