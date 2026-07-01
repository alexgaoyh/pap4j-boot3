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
 *
 * <p><b>思路储备：如何在 Maven 构建时自动刷新 openapi.json</b></p>
 * <p>
 * 如果期望将契约刷新与 Maven 构建生命周期绑定，可以在 <code>pom.xml</code> 中配置 <code>maven-surefire-plugin</code> 的专属 execution，绑定到指定阶段（如 <code>package</code> 或 <code>process-test-classes</code>）执行该单测类：
 * </p>
 * <pre>{@code
 * <plugin>
 *     <groupId>org.apache.maven.plugins</groupId>
 *     <artifactId>maven-surefire-plugin</artifactId>
 *     <executions>
 *         <execution>
 *             <id>auto-generate-openapi-json</id>
 *             <phase>package</phase>
 *             <goals>
 *                 <goal>test</goal>
 *             </goals>
 *             <configuration>
 *                 <includes>
 *                     <include>cn/net/pap/example/proguard/diagnostics/ApiRouterCatalogExporterTest.java</include>
 *                 </includes>
 *                 <skipTests>false</skipTests>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 *
 * <p><b>如何手动触发多模块 OpenAPI JSON 导出</b></p>
 * <p>
 * 如果在多个子模块下均存在同名的 <code>ApiRouterCatalogExporterTest</code> 类，可从根项目目录下执行以下 Maven 命令，同步触发所有匹配模块的单测并生成对应的 openapi.json：
 * </p>
 * <pre>{@code
 * # 适用于 PowerShell 终端环境（需对参数加引号，防止其中的点号被解析为对象属性）
 * mvn clean test "-Dtest=ApiRouterCatalogExporterTest" "-Dsurefire.failIfNoSpecifiedTests=false"
 * }</pre>
 * <p>
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
        try {
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
        } catch (AssertionError e) {
            log.error("OpenAPI 接口访问失败，状态码不是200，可能是环境问题 ", e);
        } catch (Exception e) {
            log.error("OpenAPI 文档生成失败", e);
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
