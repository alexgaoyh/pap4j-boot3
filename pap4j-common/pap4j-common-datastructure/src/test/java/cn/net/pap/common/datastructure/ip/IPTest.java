package cn.net.pap.common.datastructure.ip;

import cn.net.pap.common.datastructure.resource.TestResourceUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IPTest {

    private static final Logger log = LoggerFactory.getLogger(IPTest.class);

    @Test
    public void ipUtilTest() throws UnknownHostException {

        // ==================== 测试 isInternalIp 方法 ====================

        // 测试 null 和空白字符串
        assertFalse(IpUtil.isInternalIp(null));
        assertFalse(IpUtil.isInternalIp(""));
        assertFalse(IpUtil.isInternalIp("   "));

        // 测试无效 IP
        assertFalse(IpUtil.isInternalIp("invalid.ip"));
        assertFalse(IpUtil.isInternalIp("999.999.999.999"));

        // 测试 IPv4 内网地址
        assertTrue(IpUtil.isInternalIp("10.0.0.1"));
        assertTrue(IpUtil.isInternalIp("172.16.0.1"));
        assertTrue(IpUtil.isInternalIp("172.31.255.255"));
        assertTrue(IpUtil.isInternalIp("192.168.1.1"));
        assertTrue(IpUtil.isInternalIp("127.0.0.1"));
        assertTrue(IpUtil.isInternalIp("169.254.1.1"));
        assertTrue(IpUtil.isInternalIp("0.0.0.1"));

        // 测试 IPv4 保留地址段
        assertTrue(IpUtil.isInternalIp("192.0.2.1"));
        assertTrue(IpUtil.isInternalIp("198.18.1.1"));
        assertTrue(IpUtil.isInternalIp("198.19.1.1"));
        assertTrue(IpUtil.isInternalIp("203.0.113.1"));
        assertTrue(IpUtil.isInternalIp("224.0.0.1"));
        assertTrue(IpUtil.isInternalIp("239.255.255.255"));
        assertTrue(IpUtil.isInternalIp("240.0.0.1"));
        assertTrue(IpUtil.isInternalIp("255.255.255.255"));

        // 测试 IPv4 公网地址
        assertFalse(IpUtil.isInternalIp("8.8.8.8"));
        assertFalse(IpUtil.isInternalIp("1.1.1.1"));
        assertFalse(IpUtil.isInternalIp("114.114.114.114"));

        // 测试 IPv6 内网地址
        assertTrue(IpUtil.isInternalIp("::1"));
        assertTrue(IpUtil.isInternalIp("fe80::1"));
        assertTrue(IpUtil.isInternalIp("fc00::1"));
        assertTrue(IpUtil.isInternalIp("fd00::1"));

        // 测试 IPv6 公网地址
        assertFalse(IpUtil.isInternalIp("2001:4860:4860::8888"));

        // ==================== 测试 isInternalAddress 方法 ====================

        // 测试 null
        assertFalse(IpUtil.isInternalAddress(null));

        // 测试 IPv4 内网地址
        assertTrue(IpUtil.isInternalAddress(InetAddress.getByName("10.0.0.1")));
        assertTrue(IpUtil.isInternalAddress(InetAddress.getByName("172.16.0.1")));
        assertTrue(IpUtil.isInternalAddress(InetAddress.getByName("192.168.1.1")));

        // 测试 IPv4 公网地址
        assertFalse(IpUtil.isInternalAddress(InetAddress.getByName("8.8.8.8")));

        // 测试 IPv6 内网地址
        assertTrue(IpUtil.isInternalAddress(InetAddress.getByName("::1")));
        assertTrue(IpUtil.isInternalAddress(InetAddress.getByName("fe80::1")));
        assertTrue(IpUtil.isInternalAddress(InetAddress.getByName("fc00::1")));

        // ==================== 测试 isInternalHost 方法 ====================

        // 测试 null 和空白
        assertFalse(IpUtil.isInternalHost(null));
        assertFalse(IpUtil.isInternalHost(""));
        assertFalse(IpUtil.isInternalHost("   "));

        // 测试内网主机
        assertTrue(IpUtil.isInternalHost("localhost"));
        assertTrue(IpUtil.isInternalHost("127.0.0.1"));

        // 测试不存在的主机（会抛出异常）
        assertThrows(UnknownHostException.class, () -> IpUtil.isInternalHost("nonexistent.host.xyz"));

        // ==================== 测试 isIpInRanges 方法 ====================

        // 测试 null 和空参数
        assertFalse(IpUtil.isIpInRanges(null, null));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", null));
        assertFalse(IpUtil.isIpInRanges(null, Collections.emptyList()));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.emptyList()));

        // 测试包含 null 或空字符串的列表
        List<String> patternsWithNull = Arrays.asList(null, "", "192.168.1.1");
        assertTrue(IpUtil.isIpInRanges("192.168.1.1", patternsWithNull));

        // ==================== 测试 CIDR 匹配 ====================

        // 有效的 CIDR
        assertTrue(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.0/24")));
        assertTrue(IpUtil.isIpInRanges("192.168.1.254", Collections.singletonList("192.168.1.0/24")));
        assertFalse(IpUtil.isIpInRanges("192.168.2.1", Collections.singletonList("192.168.1.0/24")));

        // IPv6 CIDR
        assertTrue(IpUtil.isIpInRanges("fe80::1", Collections.singletonList("fe80::/10")));

        // 无效 CIDR 格式
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.0/")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.0/33")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.0/abc")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.0/24/extra")));

        // IP 版本不一致的 CIDR
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("fe80::/10")));

        // ==================== 测试范围匹配 ====================

        // 有效的范围
        assertTrue(IpUtil.isIpInRanges("192.168.1.50", Collections.singletonList("192.168.1.1-192.168.1.100")));
        assertTrue(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.1-192.168.1.100")));
        assertTrue(IpUtil.isIpInRanges("192.168.1.100", Collections.singletonList("192.168.1.1-192.168.1.100")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.101", Collections.singletonList("192.168.1.1-192.168.1.100")));

        // 无效范围格式
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.1-")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("-192.168.1.100")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.1-192.168.1.100-extra")));

        // ==================== 测试通配符匹配 ====================

        // 有效的通配符
        assertTrue(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.*")));
        assertTrue(IpUtil.isIpInRanges("192.168.1.255", Collections.singletonList("192.168.1.*")));
        assertFalse(IpUtil.isIpInRanges("192.168.2.1", Collections.singletonList("192.168.1.*")));

        // 问号通配符
        assertFalse(IpUtil.isIpInRanges("192.168.1.5", Collections.singletonList("192.168.1.?")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.10", Collections.singletonList("192.168.1.?")));

        // ==================== 测试单个 IP 匹配 ====================

        assertTrue(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.1")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.2", Collections.singletonList("192.168.1.1")));

        // ==================== 测试多个规则 ====================

        List<String> multiPatterns = Arrays.asList("10.0.0.0/8", "192.168.1.1-192.168.1.10", "172.16.*.*");
        assertTrue(IpUtil.isIpInRanges("10.1.1.1", multiPatterns));
        assertTrue(IpUtil.isIpInRanges("192.168.1.5", multiPatterns));
        assertTrue(IpUtil.isIpInRanges("172.16.1.1", multiPatterns));
        assertFalse(IpUtil.isIpInRanges("8.8.8.8", multiPatterns));

        // ==================== 测试私有方法 isInternalIPv4 的边界情况 ====================

        // 通过 isInternalAddress 间接测试 isInternalIPv4 的各种分支

        // 测试 172.16.0.0/12 边界
        assertFalse(IpUtil.isInternalIp("172.15.255.255"));  // 不属于
        assertTrue(IpUtil.isInternalIp("172.16.0.0"));      // 属于
        assertTrue(IpUtil.isInternalIp("172.31.255.255"));  // 属于
        assertFalse(IpUtil.isInternalIp("172.32.0.0"));      // 不属于

        // 测试 192.168.0.0/16
        assertFalse(IpUtil.isInternalIp("192.167.255.255"));
        assertTrue(IpUtil.isInternalIp("192.168.0.0"));
        assertTrue(IpUtil.isInternalIp("192.168.255.255"));
        assertFalse(IpUtil.isInternalIp("192.169.0.0"));

        // ==================== 测试 ipToLong 方法的异常 ====================

        assertTrue(IpUtil.isIpInRanges("::1", Collections.singletonList("::1-::2")));

        // ==================== 测试 InetAddress.getByName 解析异常 ====================

        // isIpInRange 中 catch UnknownHostException 的分支
        assertFalse(IpUtil.isIpInRanges("invalid-ip", Collections.singletonList("192.168.1.0/24")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("invalid-cidr/24")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("invalid-range-start-invalid")));

        // ==================== 测试 matchCidr 中 prefixLength 边界 ====================

        // 测试 prefixLength = 0
        assertTrue(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("0.0.0.0/0")));

        // 测试 prefixLength = 32 (IPv4 完整掩码)
        assertTrue(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.1/32")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.2", Collections.singletonList("192.168.1.1/32")));

        // 测试剩余位比较
        assertTrue(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.0/25")));
        assertFalse(IpUtil.isIpInRanges("192.168.1.129", Collections.singletonList("192.168.1.0/25")));

        // ==================== 测试 matchRange 中 parts 长度不为 2 ====================

        assertFalse(IpUtil.isIpInRanges("192.168.1.1", Collections.singletonList("192.168.1.1-192.168.1.100-extra")));

        // ==================== 测试 matchWildcard 中正则表达式匹配 ====================

        // 测试多段通配符
        assertTrue(IpUtil.isIpInRanges("10.1.2.3", Collections.singletonList("10.*.*.*")));
        assertTrue(IpUtil.isIpInRanges("10.1.2.3", Collections.singletonList("10.1.*.*")));

        // 测试超出范围的数字（正则不会匹配超过 255 的数字）
        assertFalse(IpUtil.isIpInRanges("192.168.1.256", Collections.singletonList("192.168.1.*")));

        // ==================== 测试 isInternalAddress 中 IPv6 非内网地址 ====================

        assertFalse(IpUtil.isInternalAddress(InetAddress.getByName("2001:db8::1")));

        // ==================== 测试 isInternalIPv4 中 bytes 为 null 或长度不对 ====================

    }

    // @Test
    public void ipRangeExpander() {
        Path outPath = null;
        try {
            outPath = Files.createTempFile("ip-test-", ".txt");
            expandIPRanges(TestResourceUtil.getFile("chnroute.txt").toPath().toAbsolutePath().toString(), outPath.toAbsolutePath().toString());
        } catch (IOException e) {
            log.error("展开IP段失败", e);
        } finally {
            if (outPath != null) {
                outPath.toFile().delete();
            }
        }
    }

    /**
     * 将IP段展开为单个IP地址并写入文件
     *
     * @param inputFilePath  输入文件路径，每行一个IP段
     * @param outputFilePath 输出文件路径，用于存储展开后的IP地址
     * @throws IOException 如果读写文件时发生错误
     */
    public static void expandIPRanges(String inputFilePath, String outputFilePath) throws IOException {
        // 读取输入文件并展开IP段
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] ipRanges = line.split("\\s+"); // 假设每行可能有多个IP段，用空格分隔
                for (String ipRange : ipRanges) {
                    if (ipRange.contains("/")) {
                        String[] parts = ipRange.split("/");
                        String baseIp = parts[0];
                        int prefixLength = Integer.parseInt(parts[1]);
                        InetAddress inetAddress = InetAddress.getByName(baseIp);
                        byte[] bytes = inetAddress.getAddress();
                        long startIp = ipToLong(bytes);
                        long endIp = startIp | ((1L << (32 - prefixLength)) - 1);

                        // 直接写入文件，避免内存溢出
                        for (long ip = startIp; ip <= endIp; ip++) {
                            writer.write(longToIp(ip));
                            writer.newLine();
                        }
                    } else {
                        writer.write(ipRange);
                        writer.newLine();
                    }
                }
            }
        }
    }

    // 将IP地址转换为long类型
    private static long ipToLong(byte[] bytes) {
        long result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    // 将long类型的IP地址转换为字符串
    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 8) & 0xFF) + "." +
               (ip & 0xFF);
    }


}
