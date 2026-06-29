package cn.net.pap.example.dynamic.form.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * cURL 命令行简易解析器。
 * 用于从 cURL 命令行中抽取 HTTP 请求的方法、相对路径、Query 参数字串、Headers 以及 Request Body。
 */
public class CurlParser {

    private static final Set<String> IGNORED_HEADERS = new HashSet<>(Arrays.asList(
            "host", "connection", "content-length", "accept", "user-agent",
            "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "origin",
            "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest", "referer",
            "accept-encoding", "accept-language", "cookie", "dnt",
            "cache-control", "pragma", "if-none-match", "if-modified-since",
            "upgrade-insecure-requests", "postman-token"
    ));

    public static class ParsedCurl {
        private String url = "/";
        private String method = "GET";
        private Map<String, String> headers = new HashMap<>();
        private String queryParamsStr = ""; // 保存原始未解析的 query 字符串，如 type=vip&status=active
        private String body = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }

        public String getQueryParamsStr() { return queryParamsStr; }
        public void setQueryParamsStr(String queryParamsStr) { this.queryParamsStr = queryParamsStr; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    /**
     * 解析 cURL 命令。
     *
     * @param curlCmd cURL 命令串
     * @return 解析结果
     */
    public static ParsedCurl parse(String curlCmd) {
        ParsedCurl result = new ParsedCurl();
        if (curlCmd == null || curlCmd.trim().isEmpty()) {
            return result;
        }

        List<String> tokens = tokenize(curlCmd);
        String urlToken = null;
        boolean hasBodyOption = false;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if ("-X".equals(token) || "--request".equals(token)) {
                if (i + 1 < tokens.size()) {
                    result.setMethod(tokens.get(++i).toUpperCase());
                }
            } else if ("-H".equals(token) || "--header".equals(token)) {
                if (i + 1 < tokens.size()) {
                    parseHeader(tokens.get(++i), result.getHeaders());
                }
            } else if ("-d".equals(token) || "--data".equals(token) || "--data-raw".equals(token) || "--data-binary".equals(token)) {
                hasBodyOption = true;
                if (i + 1 < tokens.size()) {
                    result.setBody(tokens.get(++i));
                }
            } else if (token.startsWith("http://") || token.startsWith("https://") || token.contains("/")) {
                if (!"curl".equalsIgnoreCase(token) && !token.startsWith("-")) {
                    urlToken = token;
                }
            }
        }

        if ("GET".equals(result.getMethod()) && hasBodyOption) {
            result.setMethod("POST");
        }

        if (urlToken != null) {
            parseUrlAndParams(urlToken, result);
        }

        return result;
    }

    private static List<String> tokenize(String curl) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < curl.length(); i++) {
            char c = curl.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    private static void parseHeader(String headerStr, Map<String, String> headers) {
        int colonIndex = headerStr.indexOf(':');
        if (colonIndex > 0) {
            String key = headerStr.substring(0, colonIndex).trim().toLowerCase();
            String value = headerStr.substring(colonIndex + 1).trim();
            if (!IGNORED_HEADERS.contains(key)) {
                headers.put(key, value);
            }
        }
    }

    private static void parseUrlAndParams(String urlToken, ParsedCurl result) {
        String cleanUrl = urlToken.trim();

        // 提取 Query 字串并直接原样保存，保证顺序不变
        String query = "";
        int questionMarkIdx = cleanUrl.indexOf('?');
        if (questionMarkIdx >= 0) {
            query = cleanUrl.substring(questionMarkIdx + 1);
            cleanUrl = cleanUrl.substring(0, questionMarkIdx);
        }
        result.setQueryParamsStr(query);

        if (cleanUrl.startsWith("http://")) {
            cleanUrl = cleanUrl.substring(7);
        } else if (cleanUrl.startsWith("https://")) {
            cleanUrl = cleanUrl.substring(8);
        }

        String path;
        if (cleanUrl.startsWith("/")) {
            path = cleanUrl;
        } else {
            int firstSlash = cleanUrl.indexOf('/');
            if (firstSlash >= 0) {
                path = cleanUrl.substring(firstSlash);
            } else {
                path = "/";
            }
        }
        result.setUrl(path);
    }
}
