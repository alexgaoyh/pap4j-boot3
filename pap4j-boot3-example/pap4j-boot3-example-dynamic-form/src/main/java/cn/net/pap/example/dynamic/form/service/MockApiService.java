package cn.net.pap.example.dynamic.form.service;

import cn.net.pap.example.dynamic.form.dto.MockApiDTO;
import cn.net.pap.example.dynamic.form.entity.MockApi;
import cn.net.pap.example.dynamic.form.repository.MockApiRepository;
import cn.net.pap.example.dynamic.form.util.CurlParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mock API 业务服务层。
 * 支持 cURL 的一键自动解构，以及针对请求参数/请求体的极高精度强一致比对匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockApiService {

    private final MockApiRepository mockApiRepository;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有配置规则
     */
    public List<MockApiDTO> listAll() {
        List<MockApi> list = mockApiRepository.findAll();
        List<MockApiDTO> dtos = new ArrayList<>(list.size());
        for (MockApi entity : list) {
            dtos.add(toDTO(entity));
        }
        return dtos;
    }

    /**
     * 保存或更新 Mock 配置。
     * 若包含 curlCommand 则自动解析并覆盖特征匹配字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public MockApiDTO save(MockApiDTO dto) {
        MockApi mockApi = (dto.id() != null) 
                ? mockApiRepository.findById(dto.id()).orElseGet(MockApi::new) 
                : new MockApi();

        mockApi.setResponseStatus(dto.responseStatus() != null ? dto.responseStatus() : 200);
        mockApi.setContentType(dto.contentType() != null ? dto.contentType() : "application/json;charset=UTF-8");
        mockApi.setResponseBody(dto.responseBody());

        if (dto.curlCommand() != null && !dto.curlCommand().trim().isEmpty()) {
            parseAndPopulateFromCurl(dto.curlCommand(), mockApi);
        } else {
            mockApi.setUrl(dto.url());
            mockApi.setMethod(dto.method() != null ? dto.method().toUpperCase() : "GET");
            mockApi.setRequestBody(dto.requestBody());
            mockApi.setRequestParams(dto.requestParams());
            mockApi.setCurlCommand(null);
            mockApi.setRequestHeaders(formatHeaders(dto.requestHeaders()));
        }

        MockApi saved = mockApiRepository.save(mockApi);
        return toDTO(saved);
    }

    /**
     * 删除指定 Mock API 配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        mockApiRepository.deleteById(id);
    }

    /**
     * 极高精度特征比对匹配。
     * 必须保证 Path 完全一致，Query 参数去空白全等，Body 去空白全等，且 Header 完全匹配。
     */
    public Optional<MockApi> matchRequest(String url, String method, Map<String, String> actualHeaders, String actualQueryStr, String actualBody) {
        List<String> methods = List.of(method.toUpperCase(), "ANY", "*");
        List<MockApi> candidates = mockApiRepository.findByUrlAndMethodIn(url, methods);

        for (MockApi candidate : candidates) {
            // 1. 比对 Header 约束
            if (!matchHeaders(candidate.getRequestHeaders(), actualHeaders)) {
                continue;
            }

            // 2. 比对 Query 串参数约束 (若配置了 Query 约束才进行严格去空白全等比对，包含参数顺序)
            if (candidate.getRequestParams() != null && !candidate.getRequestParams().trim().isEmpty()) {
                if (!matchStringIgnoreWhitespace(candidate.getRequestParams(), actualQueryStr)) {
                    continue;
                }
            }

            // 3. 比对 Request Body 约束 (若配置了 Body 约束才进行严格去空白全等比对，包含顺序)
            if (candidate.getRequestBody() != null && !candidate.getRequestBody().trim().isEmpty()) {
                if (!matchStringIgnoreWhitespace(candidate.getRequestBody(), actualBody)) {
                    continue;
                }
            }

            // 匹配成功返回
            return Optional.of(candidate);
        }

        return Optional.empty();
    }

    private void parseAndPopulateFromCurl(String curlCommand, MockApi mockApi) {
        try {
            CurlParser.ParsedCurl parsed = CurlParser.parse(curlCommand);
            mockApi.setUrl(parsed.getUrl());
            mockApi.setMethod(parsed.getMethod());
            mockApi.setRequestBody(parsed.getBody());
            mockApi.setRequestParams(parsed.getQueryParamsStr());
            mockApi.setCurlCommand(curlCommand);
            mockApi.setRequestHeaders(objectMapper.writeValueAsString(parsed.getHeaders()));
        } catch (Exception e) {
            log.error("[Mock-Save] 解析 cURL 命令行失败: ", e);
            throw new RuntimeException("cURL 命令格式有误，解析失败: " + e.getMessage());
        }
    }

    private String formatHeaders(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return "{}";
        }
        try {
            Map<String, String> map = objectMapper.readValue(jsonStr, new TypeReference<Map<String, String>>() {});
            Map<String, String> normalized = new java.util.HashMap<>();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                normalized.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new RuntimeException("非法的请求头 JSON Map 格式: " + e.getMessage());
        }
    }

    private boolean matchHeaders(String expectedHeadersJson, Map<String, String> actualHeaders) {
        if (expectedHeadersJson == null || expectedHeadersJson.trim().isEmpty() || "{}".equals(expectedHeadersJson.trim())) {
            return true;
        }
        try {
            Map<String, String> expectedMap = objectMapper.readValue(expectedHeadersJson, new TypeReference<Map<String, String>>() {});
            for (Map.Entry<String, String> entry : expectedMap.entrySet()) {
                String reqKey = entry.getKey().toLowerCase();
                String expectedVal = entry.getValue();
                String actualVal = actualHeaders.get(reqKey);
                if (actualVal == null || !actualVal.equals(expectedVal)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("[Mock-Match] 期望请求头 JSON 比对出错: ", e);
            return false;
        }
    }

    private boolean matchStringIgnoreWhitespace(String expected, String actual) {
        String cleanExpected = cleanWhitespace(expected);
        String cleanActual = cleanWhitespace(actual);
        return Objects.equals(cleanExpected, cleanActual);
    }

    private String cleanWhitespace(String str) {
        if (str == null) {
            return "";
        }
        // 去除 ASCII/Unicode 的各类空格、制表符、垂直制表符、换行、回车等空白符，以及单双引号和反引号
        return str.replaceAll("[\\s\\h\\v\\u00A0\\u2007\\u202F\\u3000'\"`]", "");
    }

    private MockApiDTO toDTO(MockApi entity) {
        return new MockApiDTO(
                entity.getId(),
                entity.getUrl(),
                entity.getMethod(),
                entity.getResponseBody(),
                entity.getResponseStatus(),
                entity.getContentType(),
                entity.getRequestHeaders(),
                entity.getRequestParams(),
                entity.getRequestBody(),
                entity.getCurlCommand()
        );
    }
}
