package cn.net.pap.example.dynamic.form.service;

import cn.net.pap.example.dynamic.form.dto.MockApiDTO;
import cn.net.pap.example.dynamic.form.entity.MockApi;
import cn.net.pap.example.dynamic.form.repository.MockApiRepository;
import cn.net.pap.example.dynamic.form.util.CurlParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** Mock 拦截端点前缀，规则 URL 的存储与匹配均以剥离该前缀后的相对路径为准。 */
    private static final String MOCK_PREFIX = "/api/mock";

    private final MockApiRepository mockApiRepository;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有配置规则
     * @return 结果集合
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
     * <p>仅新建记录（id 为空）时才允许用 curlCommand 反解特征字段；编辑已有记录时一律以表单字段为准，
     * 避免旧 cURL 原文在保存瞬间覆盖用户手工修改的匹配条件。</p>
     * @param dto
     * @return 处理结果对象
     */
    @Transactional(rollbackFor = Exception.class)
    public MockApiDTO save(MockApiDTO dto) {
        MockApi mockApi = (dto.id() != null) 
                ? mockApiRepository.findById(dto.id()).orElseGet(MockApi::new) 
                : new MockApi();

        mockApi.setResponseStatus(dto.responseStatus() != null ? dto.responseStatus() : 200);
        mockApi.setContentType(dto.contentType() != null ? dto.contentType() : "application/json;charset=UTF-8");
        mockApi.setResponseBody(dto.responseBody());
        mockApi.setResponseHeaders(formatResponseHeaders(dto.responseHeaders()));
        mockApi.setDelayMs(dto.delayMs() != null ? dto.delayMs() : 0);

        boolean creating = dto.id() == null;
        if (creating && dto.curlCommand() != null && !dto.curlCommand().trim().isEmpty()) {
            parseAndPopulateFromCurl(dto.curlCommand(), mockApi);
        } else {
            applyExplicitFields(dto, mockApi);
        }

        MockApi saved = mockApiRepository.save(mockApi);
        return toDTO(saved);
    }

    /**
     * 以表单显式字段装配匹配条件，cURL 原文仅作为历史记录保存，不参与反解。
     *
     * @param dto     前端提交的配置
     * @param mockApi 待装配的实体
     */
    private void applyExplicitFields(MockApiDTO dto, MockApi mockApi) {
        mockApi.setUrl(normalizeMockPath(dto.url()));
        mockApi.setMethod(dto.method() != null ? dto.method().toUpperCase() : "GET");
        mockApi.setRequestBody(dto.requestBody());
        mockApi.setRequestParams(dto.requestParams());
        mockApi.setRequestHeaders(formatHeaders(dto.requestHeaders()));
        mockApi.setCurlCommand(
                dto.curlCommand() == null || dto.curlCommand().trim().isEmpty() ? null : dto.curlCommand());
    }

    /**
     * 归一化规则 URL：剥离 {@code /api/mock} 前缀，保证与 {@code handleMock} 传入的相对路径口径一致。
     * 否则从本 Mock 终端复制出的 cURL 会带着前缀入库，导致该规则永远无法命中。
     *
     * @param url 原始 URL
     * @return 剥离前缀后的相对路径，至少为 {@code /}
     */
    private static String normalizeMockPath(String url) {
        if (url == null) {
            return "/";
        }
        String path = url.trim();
        if (path.regionMatches(true, 0, MOCK_PREFIX, 0, MOCK_PREFIX.length())) {
            path = path.substring(MOCK_PREFIX.length());
        }
        if (path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 读取侧兼容：历史数据中可能残留带 {@code /api/mock} 前缀的规则，匹配时一并查询。
     *
     * @param url 前端实际请求的相对路径
     * @return 需要参与匹配的 URL 候选列表
     */
    private static List<String> resolveUrlCandidates(String url) {
        String path = (url == null || url.isEmpty()) ? "/" : url;
        if (path.regionMatches(true, 0, MOCK_PREFIX, 0, MOCK_PREFIX.length())) {
            return List.of(path);
        }
        return List.of(path, MOCK_PREFIX + path);
    }

    /**
     * 删除指定 Mock API 配置
     * @param id
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        mockApiRepository.deleteById(id);
    }

    /**
     * 极高精度特征比对匹配。
     * 必须保证 Path 完全一致，Query 参数去空白全等，Body 去空白全等，且 Header 完全匹配。
     * @param url
     * @param method
     * @param actualHeaders
     * @param actualQueryStr
     * @param actualBody
     * @return 结果集合
     */
    public Optional<MockApi> matchRequest(String url, String method, Map<String, String> actualHeaders, String actualQueryStr, String actualBody) {
        List<String> methods = List.of(method.toUpperCase(), "ANY", "*");
        List<MockApi> candidates = new ArrayList<>();
        for (String candidateUrl : resolveUrlCandidates(url)) {
            candidates.addAll(mockApiRepository.findByUrlAndMethodIn(candidateUrl, methods));
        }

        for (MockApi candidate : candidates) {
            // 1. 比对 Header 约束
            if (!matchHeaders(candidate.getRequestHeaders(), actualHeaders)) {
                continue;
            }

            // 2. 比对 Query 串参数约束 (语义化比对，与顺序无关)
            if (candidate.getRequestParams() != null && !candidate.getRequestParams().trim().isEmpty()) {
                if (!matchQueryParams(candidate.getRequestParams(), actualQueryStr)) {
                    continue;
                }
            }

            // 3. 比对 Request Body 约束 (语义化比对，与顺序无关)
            if (candidate.getRequestBody() != null && !candidate.getRequestBody().trim().isEmpty()) {
                if (!matchBodySemantic(candidate.getRequestBody(), actualBody)) {
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
            mockApi.setUrl(normalizeMockPath(parsed.getUrl()));
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
            log.error("非法的请求头 JSON Map 格式", e);
            throw new RuntimeException("非法的请求头 JSON Map 格式: " + e.getMessage());
        }
    }

    private String formatResponseHeaders(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return "{}";
        }
        try {
            Map<String, String> map = objectMapper.readValue(jsonStr, new TypeReference<Map<String, String>>() {});
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("非法的响应头 JSON Map 格式", e);
            throw new RuntimeException("非法的响应头 JSON Map 格式: " + e.getMessage());
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

    private boolean matchQueryParams(String expectedQuery, String actualQuery) {
        if (expectedQuery == null || expectedQuery.trim().isEmpty()) {
            return actualQuery == null || actualQuery.trim().isEmpty();
        }
        Map<String, List<String>> expectedMap = parseQueryParams(expectedQuery);
        Map<String, List<String>> actualMap = parseQueryParams(actualQuery);
        return Objects.equals(expectedMap, actualMap);
    }

    private Map<String, List<String>> parseQueryParams(String queryStr) {
        if (queryStr == null || queryStr.trim().isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        String cleanQuery = queryStr.trim();
        if (cleanQuery.startsWith("?")) {
            cleanQuery = cleanQuery.substring(1);
        }
        Map<String, List<String>> params = new HashMap<>();
        String[] pairs = cleanQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf("=");
            String key;
            String value;
            try {
                if (idx > 0) {
                    key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                } else {
                    key = java.net.URLDecoder.decode(pair, "UTF-8");
                    value = "";
                }
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            } catch (Exception e) {
                log.error("[Mock-Service] URL 解码 Query 键值对发生异常: {}", pair, e);
                if (idx > 0) {
                    key = pair.substring(0, idx);
                    value = pair.substring(idx + 1);
                } else {
                    key = pair;
                    value = "";
                }
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
        for (List<String> values : params.values()) {
            java.util.Collections.sort(values);
        }
        return params;
    }

    private boolean matchBodySemantic(String expectedBody, String actualBody) {
        if (expectedBody == null || expectedBody.trim().isEmpty()) {
            return actualBody == null || actualBody.trim().isEmpty();
        }
        try {
            JsonNode expectedNode = objectMapper.readTree(expectedBody);
            JsonNode actualNode = objectMapper.readTree(actualBody);
            return expectedNode.equals(actualNode);
        } catch (Exception e) {
            log.error("JSON 比对失败，回退到忽略空白比较", e);
            return matchStringIgnoreWhitespace(expectedBody, actualBody);
        }
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
                entity.getResponseHeaders(),
                entity.getDelayMs(),
                entity.getCurlCommand()
        );
    }
}
