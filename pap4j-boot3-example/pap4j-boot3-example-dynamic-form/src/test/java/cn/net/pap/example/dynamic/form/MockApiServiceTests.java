package cn.net.pap.example.dynamic.form;

import cn.net.pap.example.dynamic.form.dto.MockApiDTO;
import cn.net.pap.example.dynamic.form.service.MockApiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Mock API 语义化无序匹配 (JSON Key 顺序无关、Query 参数顺序无关)。
 * <p>
 * 特殊说明：单测方法上标注的 {@link org.springframework.transaction.annotation.Transactional} 
 * 仅用于在单元测试运行完毕后自动回滚数据库状态以进行测试数据隔离，此用法属于测试框架特性，不违反生产代码中“@Transactional 仅限标注在 Service 层方法上”的事务设计红线。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MockApiServiceTests {

    private final MockApiService mockApiService;

    public MockApiServiceTests(MockApiService mockApiService) {
        this.mockApiService = mockApiService;
    }

    @Test
    @Transactional
    void testSemanticQueryParamMatching() {
        // 1. 保存期望 Query 为 page=1&size=10 的规则
        MockApiDTO dto = new MockApiDTO(
                null,
                "/api/query-test",
                "GET",
                "{}",
                200,
                "application/json;charset=UTF-8",
                "{}",
                "page=1&size=10",
                "",
                "{}",
                0,
                ""
        );
        mockApiService.save(dto);

        // 2. 使用顺序不同的 Query (size=10&page=1) 进行匹配
        var matched = mockApiService.matchRequest("/api/query-test", "GET", new HashMap<>(), "size=10&page=1", "");
        assertTrue(matched.isPresent(), "应当无视参数物理顺序匹配成功");
    }

    @Test
    @Transactional
    void testSemanticJsonBodyMatching() {
        // 1. 保存期望 JSON Body 为 {"name":"Alice","age":20} 的规则
        MockApiDTO dto = new MockApiDTO(
                null,
                "/api/body-test",
                "POST",
                "{}",
                200,
                "application/json;charset=UTF-8",
                "{}",
                "",
                "{\"name\":\"Alice\",\"age\":20}",
                "{}",
                0,
                ""
        );
        mockApiService.save(dto);

        // 2. 发送 Key 顺序不同且带空格换行的 JSON 进行匹配
        String actualBody = "{\n  \"age\": 20,\n  \"name\": \"Alice\"\n}";
        var matched = mockApiService.matchRequest("/api/body-test", "POST", new HashMap<>(), "", actualBody);
        assertTrue(matched.isPresent(), "应当无视 JSON Key 物理顺序与格式化匹配成功");
    }

    @Test
    @Transactional
    void testSemanticJsonArrayMatching() {
        // 1. 保存期望 JSON Body 为 JSON 数组且包含无序 Key 对象的规则
        MockApiDTO dto = new MockApiDTO(
                null,
                "/api/array-test",
                "POST",
                "{}",
                200,
                "application/json;charset=UTF-8",
                "{}",
                "",
                "[{\"name\":\"Alice\",\"age\":20},{\"name\":\"Bob\",\"age\":30}]",
                "{}",
                0,
                ""
        );
        mockApiService.save(dto);

        // 2. 发送元素对象 Key 顺序颠倒的 JSON 数组进行匹配
        String actualBody = "[\n  {\"age\": 20, \"name\": \"Alice\"},\n  {\"age\": 30, \"name\": \"Bob\"}\n]";
        var matched = mockApiService.matchRequest("/api/array-test", "POST", new HashMap<>(), "", actualBody);
        assertTrue(matched.isPresent(), "应当支持 JSON 数组内包含的 Object 键物理顺序无关匹配");
    }
}
