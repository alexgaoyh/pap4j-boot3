package cn.net.pap.common.datastructure.ip;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * IP 地址内网判断工具类。
 * <p>
 * 支持 IPv4 和 IPv6 地址的内网判断，基于 JDK 原生 API 和私有地址段规则。
 * 适用于服务端内网访问限制、日志分析筛选公网 IP、网络拓扑判断等场景。
 * </p>
 *
 * <p>判断规则包括：</p>
 * <ul>
 *     <li>环回地址（Loopback）：127.0.0.0/8, ::1</li>
 *     <li>链路本地地址（Link-Local）：169.254.0.0/16, fe80::/10</li>
 *     <li>站点本地地址（Site-Local）：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7</li>
 *     <li>其他保留地址：组播地址、E类保留地址、文档测试地址等</li>
 * </ul>
 *
 * @author
 * @since 17
 */
public class IpUtil {

    /**
     * <p>日志记录器。</p>
     */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IpUtil.class);

    /**
     * 私有构造方法，防止实例化工具类。
     * <p>
     * 工具类中的所有方法均为静态方法，无需创建实例。
     * </p>
     */
    private IpUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 判断给定的 IP 地址字符串是否为内网地址。
     *
     * @param ip IP 地址字符串，支持 IPv4 和 IPv6 格式
     * @return 如果是内网地址返回 {@code true}，否则返回 {@code false}；
     * 如果 IP 地址为 {@code null}、空白字符串或解析失败，返回 {@code false}
     */
    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return isInternalAddress(address);
        } catch (UnknownHostException e) {
            log.error("IP 解析失败, ip={}", ip, e);
            return false;
        }
    }

    /**
     * 判断给定的 {@link InetAddress} 对象是否为内网地址。
     *
     * @param address InetAddress 对象，不能为 {@code null}
     * @return 如果是内网地址返回 {@code true}，否则返回 {@code false}；
     * 如果 address 为 {@code null}，返回 {@code false}
     */
    public static boolean isInternalAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        // 环回地址
        if (address.isLoopbackAddress()) {
            return true;
        }
        // 链路本地地址
        if (address.isLinkLocalAddress()) {
            return true;
        }
        // 站点本地地址（注意：Java 的 isSiteLocalAddress 不支持 fc00::/7）
        if (address.isSiteLocalAddress()) {
            return true;
        }

        // 手动判断 IPv6 唯一本地地址 (ULA) fc00::/7
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            // fc00::/7 的第一个字节应该是 0xfc 或 0xfd
            if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
                return true;
            }
        }

        // IPv4 额外保留地址段判断
        if (address instanceof Inet4Address) {
            return isInternalIPv4(address.getAddress());
        }
        return false;
    }

    /**
     * 判断 IPv4 地址是否为内网或保留地址。
     * <p>
     * 除了标准的私有地址段外，还包含了以下保留地址段：
     * <ul>
     *     <li>0.0.0.0/8 - 当前网络（通常视为内网）</li>
     *     <li>127.0.0.0/8 - 环回地址</li>
     *     <li>169.254.0.0/16 - 链路本地地址</li>
     *     <li>192.0.2.0/24 - 文档和示例地址（TEST-NET-1）</li>
     *     <li>198.18.0.0/15 - 网络基准测试地址</li>
     *     <li>203.0.113.0/24 - 文档和示例地址（TEST-NET-3）</li>
     *     <li>224.0.0.0/4 - 组播地址</li>
     *     <li>240.0.0.0/4 - 保留地址（E类地址）</li>
     * </ul>
     * </p>
     *
     * @param bytes IPv4 地址的字节数组，长度为 4，不能为 {@code null}
     * @return 如果是内网或保留地址返回 {@code true}，否则返回 {@code false}
     */
    private static boolean isInternalIPv4(byte[] bytes) {
        if (bytes == null || bytes.length != 4) {
            return false;
        }

        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        int third = bytes[2] & 0xFF;

        // 标准私有地址段 10.0.0.0/8
        if (first == 10) {
            return true;
        }
        // 172.16.0.0/12
        if (first == 172 && (second >= 16 && second <= 31)) {
            return true;
        }
        // 192.168.0.0/16
        if (first == 192 && second == 168) {
            return true;
        }

        // 环回地址// 127.0.0.0/8
        if (first == 127) {
            return true;
        }

        // 链路本地地址// 169.254.0.0/16
        if (first == 169 && second == 254) {
            return true;
        }

        // 当前网络（通常视为内网）// 0.0.0.0/8
        if (first == 0) {
            return true;
        }

        // 文档和测试地址// 192.0.2.0/24 (TEST-NET-1)
        if (first == 192 && second == 0 && third == 2) {
            return true;
        }
        // 198.18.0.0/15 (Benchmarking)
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        // 203.0.113.0/24 (TEST-NET-3)
        if (first == 203 && second == 0 && third == 113) {
            return true;
        }

        // 组播地址// 224.0.0.0/4
        if (first >= 224 && first <= 239) {
            return true;
        }

        // 保留地址（E类地址）// 240.0.0.0/4
        if (first >= 240 && first <= 255) {
            return true;
        }

        return false;
    }

    /**
     * 判断给定的主机名是否为内网地址。
     * <p>
     * 该方法会解析主机名为 {@link InetAddress}，然后判断是否为内网地址。
     * </p>
     *
     * @param host 主机名或 IP 地址字符串，不能为 {@code null}
     * @return 如果是内网地址返回 {@code true}，否则返回 {@code false}
     * @throws UnknownHostException 如果无法解析主机名时抛出此异常
     */
    public static boolean isInternalHost(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            return false;
        }
        return isInternalAddress(InetAddress.getByName(host));
    }


    /**
     * 判断目标 IP 是否在配置的 IP 地址段列表中
     *
     * @param targetIp   目标 IP 地址（真实 IP）
     * @param ipPatterns IP 地址段配置列表，支持 CIDR、单个IP、范围等格式
     * @return 如果目标 IP 在任一配置段内返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isIpInRanges(String targetIp, List<String> ipPatterns) {
        if (targetIp == null || targetIp.isBlank() || ipPatterns == null || ipPatterns.isEmpty()) {
            return false;
        }

        for (String pattern : ipPatterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (isIpInRange(targetIp, pattern.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断目标 IP 是否匹配单个 IP 地址段规则
     *
     * @param targetIp 目标 IP 地址
     * @param pattern  IP 地址段规则（CIDR、单个IP、范围、通配符）
     * @return 如果匹配则返回 {@code true}
     */
    private static boolean isIpInRange(String targetIp, String pattern) {
        try {
            // 1. CIDR 格式：192.168.1.0/24
            if (pattern.contains("/")) {
                return matchCidr(targetIp, pattern);
            }

            // 2. IP 范围格式：192.168.1.1-192.168.1.100
            if (pattern.contains("-")) {
                return matchRange(targetIp, pattern);
            }

            // 3. 通配符格式：192.168.1.*
            if (pattern.contains("*")) {
                return matchWildcard(targetIp, pattern);
            }

            // 4. 单个 IP 格式：192.168.1.1
            return targetIp.equals(pattern);

        } catch (UnknownHostException e) {
            log.error("IP 地址段匹配解析失败, targetIp={}, pattern={}", targetIp, pattern, e);
            return false;
        }
    }

    /**
     * CIDR 格式匹配
     * <p>
     * 示例：192.168.1.0/24 匹配 192.168.1.1 ~ 192.168.1.254
     * </p>
     *
     * @param targetIp 目标 IP
     * @param cidr     CIDR 格式字符串
     * @return 是否匹配
     * @throws UnknownHostException IP 解析异常
     */
    private static boolean matchCidr(String targetIp, String cidr) throws UnknownHostException {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return false;
        }

        String networkIp = parts[0];
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            log.error("CIDR 前缀长度解析失败, cidr={}", cidr, e);
            return false;
        }

        // 获取 IP 地址的字节数组
        byte[] targetBytes = InetAddress.getByName(targetIp).getAddress();
        byte[] networkBytes = InetAddress.getByName(networkIp).getAddress();

        // 检查 IP 版本是否一致
        if (targetBytes.length != networkBytes.length) {
            return false;
        }

        // 计算掩码并比较
        int maxPrefixLength = targetBytes.length * 8;
        if (prefixLength < 0 || prefixLength > maxPrefixLength) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        // 比较完整字节
        for (int i = 0; i < fullBytes; i++) {
            if (targetBytes[i] != networkBytes[i]) {
                return false;
            }
        }

        // 比较剩余位
        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            int targetLastByte = targetBytes[fullBytes] & 0xFF;
            int networkLastByte = networkBytes[fullBytes] & 0xFF;
            if ((targetLastByte & mask) != (networkLastByte & mask)) {
                return false;
            }
        }

        return true;
    }

    /**
     * IP 范围格式匹配
     * <p>
     * 示例：192.168.1.1-192.168.1.100
     * </p>
     *
     * @param targetIp 目标 IP
     * @param range    IP 范围字符串
     * @return 是否在范围内
     * @throws UnknownHostException IP 解析异常
     */
    private static boolean matchRange(String targetIp, String range) throws UnknownHostException {
        String[] parts = range.split("-");
        if (parts.length != 2) {
            return false;
        }

        String startIp = parts[0].trim();
        String endIp = parts[1].trim();

        // 增加空字符串验证
        if (startIp.isEmpty() || endIp.isEmpty()) {
            return false;
        }

        InetAddress targetAddr = InetAddress.getByName(targetIp);
        InetAddress startAddr = InetAddress.getByName(parts[0].trim());
        InetAddress endAddr = InetAddress.getByName(parts[1].trim());

        // 检查 IP 版本一致
        if (targetAddr.getAddress().length != startAddr.getAddress().length) {
            return false;
        }

        BigInteger target = new BigInteger(1, targetAddr.getAddress());
        BigInteger start = new BigInteger(1, startAddr.getAddress());
        BigInteger end = new BigInteger(1, endAddr.getAddress());

        return target.compareTo(start) >= 0 && target.compareTo(end) <= 0;
    }

    /**
     * 通配符格式匹配
     * <p>
     * 示例：192.168.1.* 匹配 192.168.1.0 ~ 192.168.1.255
     * </p>
     *
     * @param targetIp 目标 IP
     * @param wildcard 通配符格式字符串
     * @return 是否匹配
     */
    private static boolean matchWildcard(String targetIp, String wildcard) {
        // 将通配符转换为正则表达式
        String regex = wildcard.replace(".", "\\.").replace("*", "\\d{1,3}").replace("?", "\\d{1,3}");

        // 对于 IPv4 地址，确保每个段都是 0-255 的数字
        regex = regex.replace("\\d{1,3}", "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)");

        return targetIp.matches(regex);
    }

    /**
     * IPv4 地址转换为 long 类型（用于范围比较） 支持 IPv6
     *
     * @param ip IPv4 地址字符串
     * @return long 类型的 IP 值
     * @throws UnknownHostException IP 解析异常
     */
    private static BigInteger ipToLong(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        byte[] bytes = address.getAddress();
        return new BigInteger(1, bytes);
    }

}