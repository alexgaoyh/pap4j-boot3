package cn.net.pap.example.nginx.support;

/**
 * <p>路径前缀处理工具。</p>
 * <p>统一 {@code normalize} 与「按路径段匹配前缀」逻辑，避免各处理器重复实现导致行为不一致。</p>
 *
 * @author alexgaoyh
 */
public final class PathPrefixSupport {

    private PathPrefixSupport() {
    }

    /**
     * 归一化路径前缀，去除结尾多余的 {@code /}。
     *
     * @param path 原始路径
     * @return 归一化路径
     */
    public static String normalize(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * 判断请求路径是否命中指定前缀（按路径段匹配，避免 {@code /apixxx} 误命中 {@code /api}）。
     *
     * @param requestPath 请求路径
     * @param prefix      路径前缀
     * @return 是否命中
     */
    public static boolean matches(String requestPath, String prefix) {
        if (requestPath == null) {
            return false;
        }
        String normalized = normalize(prefix);
        if ("/".equals(normalized)) {
            return true;
        }
        return requestPath.equals(normalized) || requestPath.startsWith(normalized + "/");
    }

}
